from main import CHUNK_OVERLAP_WORDS, CHUNK_TARGET_WORDS, chunk_text


def test_short_text_produces_one_chunk():
    chunks = chunk_text("This is a short paragraph.\n\nAnother short one.")
    assert len(chunks) == 1
    assert "short paragraph" in chunks[0]
    assert "Another short one" in chunks[0]


def test_long_paragraph_splits_into_overlapping_windows():
    words = [f"word{i}" for i in range(CHUNK_TARGET_WORDS * 2 + 10)]
    text = " ".join(words)

    chunks = chunk_text(text)

    assert len(chunks) >= 2
    for chunk in chunks:
        assert len(chunk.split()) <= CHUNK_TARGET_WORDS
    # overlap: the tail of chunk 0 should reappear at the head of chunk 1
    tail_of_first = chunks[0].split()[-CHUNK_OVERLAP_WORDS:]
    head_of_second = chunks[1].split()[:CHUNK_OVERLAP_WORDS]
    assert tail_of_first == head_of_second


def test_multiple_short_paragraphs_pack_into_one_chunk_until_the_limit():
    paragraphs = ["one two three"] * 10
    text = "\n\n".join(paragraphs)

    chunks = chunk_text(text)

    assert len(chunks) == 1
    assert len(chunks[0].split()) == 30


def test_empty_text_produces_no_chunks():
    assert chunk_text("") == []
    assert chunk_text("   \n\n   ") == []
