from __future__ import annotations

import io
import logging
from typing import Optional

import pdfplumber
import spacy
from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("nlp-sidecar")

CHUNK_TARGET_WORDS = 200
CHUNK_OVERLAP_WORDS = 30
MIN_EXTRACTED_CHARS_BEFORE_OCR_NEEDED = 20

app = FastAPI(title="AlphaGraph NLP Sidecar")

_embedding_model: Optional[SentenceTransformer] = None
_nlp = None


def embedding_model() -> SentenceTransformer:
    global _embedding_model
    if _embedding_model is None:
        _embedding_model = SentenceTransformer("all-MiniLM-L6-v2")
    return _embedding_model


def nlp_pipeline():
    global _nlp
    if _nlp is None:
        _nlp = spacy.load("en_core_web_sm")
    return _nlp


class EntitySpan(BaseModel):
    text: str
    label: str


class DocumentChunk(BaseModel):
    index: int
    text: str
    wordCount: int
    embedding: list[float]
    entities: list[EntitySpan]


class ProcessedDocument(BaseModel):
    pageCount: int
    fullText: str
    needsOcr: bool
    chunks: list[DocumentChunk]


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


def parse_pdf(pdf_bytes: bytes) -> tuple[str, int]:
    """Text-layer extraction only - no OCR fallback yet (Tesseract binary isn't installed on
    this dev machine). Scanned/image-only PDFs come back near-empty; the caller flags that via
    needsOcr rather than silently returning nothing."""
    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        page_texts = [page.extract_text() or "" for page in pdf.pages]
        return "\n\n".join(page_texts).strip(), len(pdf.pages)


def chunk_text(text: str) -> list[str]:
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
    chunks: list[str] = []
    current_words: list[str] = []

    for paragraph in paragraphs:
        paragraph_words = paragraph.split()
        if len(current_words) + len(paragraph_words) <= CHUNK_TARGET_WORDS:
            current_words.extend(paragraph_words)
            continue

        if current_words:
            chunks.append(" ".join(current_words))
            current_words = current_words[-CHUNK_OVERLAP_WORDS:]

        if len(paragraph_words) > CHUNK_TARGET_WORDS:
            for i in range(0, len(paragraph_words), CHUNK_TARGET_WORDS - CHUNK_OVERLAP_WORDS):
                window = paragraph_words[i:i + CHUNK_TARGET_WORDS]
                chunks.append(" ".join(window))
            current_words = []
        else:
            current_words.extend(paragraph_words)

    if current_words:
        chunks.append(" ".join(current_words))

    return chunks


def extract_entities(text: str) -> list[EntitySpan]:
    doc = nlp_pipeline()(text)
    return [EntitySpan(text=ent.text, label=ent.label_) for ent in doc.ents]


@app.post("/documents/process", response_model=ProcessedDocument)
async def process_document(file: UploadFile = File(...)) -> ProcessedDocument:
    if file.content_type not in ("application/pdf", "application/octet-stream"):
        raise HTTPException(status_code=400, detail=f"Unsupported content type: {file.content_type}")

    pdf_bytes = await file.read()
    try:
        full_text, page_count = parse_pdf(pdf_bytes)
    except Exception as exc:
        logger.exception("Failed to parse PDF %s", file.filename)
        raise HTTPException(status_code=422, detail=f"Could not parse PDF: {exc}") from exc

    needs_ocr = len(full_text) < MIN_EXTRACTED_CHARS_BEFORE_OCR_NEEDED
    if needs_ocr:
        logger.warning("Document %s extracted only %d chars - likely a scanned/image PDF, needs OCR", file.filename, len(full_text))
        return ProcessedDocument(pageCount=page_count, fullText=full_text, needsOcr=True, chunks=[])

    chunk_texts = chunk_text(full_text)
    embeddings = embedding_model().encode(chunk_texts, normalize_embeddings=True)

    chunks = [
        DocumentChunk(
            index=i,
            text=chunk_texts[i],
            wordCount=len(chunk_texts[i].split()),
            embedding=embeddings[i].tolist(),
            entities=extract_entities(chunk_texts[i]),
        )
        for i in range(len(chunk_texts))
    ]

    return ProcessedDocument(pageCount=page_count, fullText=full_text, needsOcr=False, chunks=chunks)
