package com.alphagraph.corporate.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * md5 of a document's text, as lowercase hex - deliberately the same function Postgres' {@code
 * md5(text)} computes over UTF-8, because {@code corporate.documents.text_hash} was backfilled in
 * SQL (V21) and new documents are hashed here; the two have to agree. Not a security use, just a
 * cheap fingerprint to recognise the same attachment text arriving twice.
 */
final class TextHash {

    private TextHash() {
    }

    static String of(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required by the JDK specification", e);
        }
    }
}
