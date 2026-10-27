package com.alphagraph.corporate.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextHashTest {

    @Test
    void matchesTheStandardMd5VectorsPostgresProduces() {
        // V21 backfilled text_hash with Postgres' md5(text); these are the well-known digests it returns.
        assertThat(TextHash.of("")).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
        assertThat(TextHash.of("hello")).isEqualTo("5d41402abc4b2a76b9719d911017c592");
    }

    @Test
    void identicalTextAlwaysHashesTheSameAndDifferentTextDoesNot() {
        assertThat(TextHash.of("results attachment")).isEqualTo(TextHash.of("results attachment"));
        assertThat(TextHash.of("results attachment")).isNotEqualTo(TextHash.of("results attachment."));
    }

    @Test
    void isThirtyTwoLowercaseHexCharactersSoItFitsTheVarchar32Column() {
        assertThat(TextHash.of("any text at all, including ₹ and é")).matches("[0-9a-f]{32}");
    }
}
