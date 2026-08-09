package com.alphagraph.reference.securitymaster;

import com.alphagraph.reference.api.SecurityMasterEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityMasterNormalizerTest {

    private final SecurityMasterNormalizer normalizer = new SecurityMasterNormalizer();

    @Test
    void parsesUpperCaseListingDateAndFaceValue() {
        RawSecurityMasterRow raw = new RawSecurityMasterRow("SBIN", "State Bank of India", "EQ", "01-MAR-1995", "1", "INE062A01020");

        SecurityMasterEntry entry = normalizer.normalize(raw);

        assertThat(entry.symbol()).isEqualTo("SBIN");
        assertThat(entry.companyName()).isEqualTo("State Bank of India");
        assertThat(entry.isin()).isEqualTo("INE062A01020");
        assertThat(entry.listingDate()).isEqualTo(LocalDate.of(1995, 3, 1));
        assertThat(entry.faceValue()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void blankListingDateAndFaceValueNormalizeToNull() {
        RawSecurityMasterRow raw = new RawSecurityMasterRow("SBIN", "State Bank of India", "EQ", "", "", "INE062A01020");

        SecurityMasterEntry entry = normalizer.normalize(raw);

        assertThat(entry.listingDate()).isNull();
        assertThat(entry.faceValue()).isNull();
    }
}
