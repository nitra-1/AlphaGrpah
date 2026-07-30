package com.alphagraph.corporate.processing;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.util.List;
import java.util.UUID;

/**
 * Upserts on (document_id, chunk_index) - idempotent per docs/002_Engine_Architecture.md §2.
 * {@code embedding} is stored as a plain Postgres {@code real[]}, not pgvector's {@code vector}
 * type (see the V2 migration's own comment for why) - {@code Connection.createArrayOf("real", ...)}
 * is the standard JDBC way to bind a Java array to a Postgres array column; {@code setObject}
 * with a raw {@code float[]} isn't enough on its own.
 */
@Component
public class DocumentChunkWriter {

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the persisted chunk's actual id, for {@link DocumentEntityWriter} to reference -
     * RETURNING is required here (not just the freshly-generated UUID passed in) because on a
     * conflict, the existing row's id is kept, not the new one this call generated.
     */
    public UUID write(UUID documentId, int chunkIndex, String chunkText, int wordCount, float[] embedding) {
        Float[] boxedEmbedding = new Float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            boxedEmbedding[i] = embedding[i];
        }
        Array sqlArray = jdbcTemplate.execute((ConnectionCallback<Array>) connection -> connection.createArrayOf("real", boxedEmbedding));

        List<UUID> result = jdbcTemplate.query(
            """
            INSERT INTO corporate.document_chunks (id, document_id, chunk_index, chunk_text, word_count, embedding)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (document_id, chunk_index) DO UPDATE SET
                chunk_text = EXCLUDED.chunk_text, word_count = EXCLUDED.word_count, embedding = EXCLUDED.embedding
            RETURNING id
            """,
            (rs, rowNum) -> (UUID) rs.getObject("id"),
            UUID.randomUUID(), documentId, chunkIndex, chunkText, wordCount, sqlArray
        );
        return result.get(0);
    }
}
