package com.alphagraph.ownership.interpretation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipantClassifierTest {

    @Test
    void mutualFundClassifiesAtHighConfidence() {
        ParticipantClassification result = ParticipantClassifier.classify("SBI MUTUAL FUND");
        assertThat(result.type()).isEqualTo(ParticipantType.MUTUAL_FUND);
        assertThat(result.confidence()).isEqualTo(95);
    }

    @Test
    void insuranceClassifiesAtHighConfidence() {
        ParticipantClassification result = ParticipantClassifier.classify("LIFE INSURANCE CORPORATION OF INDIA");
        assertThat(result.type()).isEqualTo(ParticipantType.INSURANCE);
        assertThat(result.confidence()).isEqualTo(95);
    }

    @Test
    void corporateSuffixClassifiesAt80Confidence() {
        // Real production name, already captured by Sprint 1.
        ParticipantClassification result = ParticipantClassifier.classify("L7 HITECH PRIVATE LIMITED");
        assertThat(result.type()).isEqualTo(ParticipantType.CORPORATE);
        assertThat(result.confidence()).isEqualTo(80);
    }

    @Test
    void foreignFundStructureSuffixClassifiesAsFpiFiiAtOnly60Confidence() {
        // Real production name - PCC is a real signal, not proof, per the user's explicit correction.
        ParticipantClassification result = ParticipantClassifier.classify("AL MAHA INVESTMENT FUND PCC ONYX STRATEGY");
        assertThat(result.type()).isEqualTo(ParticipantType.FPI_FII);
        assertThat(result.confidence()).isEqualTo(60);
    }

    @Test
    void subFundSuffixAlsoClassifiesAsFpiFii() {
        ParticipantClassification result = ParticipantClassifier.classify("MGO HIGH CONVICTION FUND INCORPORATED VCC SUBFUND");
        assertThat(result.type()).isEqualTo(ParticipantType.FPI_FII);
    }

    @Test
    void personalNameShapeClassifiesAsIndividual() {
        // Real production name, already captured by Sprint 1.
        ParticipantClassification result = ParticipantClassifier.classify("JAGID VANITABEN RAJENDRAPRASAD");
        assertThat(result.type()).isEqualTo(ParticipantType.INDIVIDUAL);
        assertThat(result.confidence()).isEqualTo(70);
    }

    @Test
    void aifKeywordClassifiesAt90Confidence() {
        ParticipantClassification result = ParticipantClassifier.classify("XYZ AIF CATEGORY III FUND");
        assertThat(result.type()).isEqualTo(ParticipantType.AIF);
        assertThat(result.confidence()).isEqualTo(90);
    }

    @Test
    void propKeywordClassifiesAsPropDesk() {
        ParticipantClassification result = ParticipantClassifier.classify("XYZ PROP TRADING DESK");
        assertThat(result.type()).isEqualTo(ParticipantType.PROP_DESK);
    }

    @Test
    void quantKeywordClassifiesAsQuantHft() {
        ParticipantClassification result = ParticipantClassifier.classify("XYZ QUANT STRATEGIES");
        assertThat(result.type()).isEqualTo(ParticipantType.QUANT_HFT);
    }

    @Test
    void unrecognizedShapeClassifiesAsUnknownWithZeroConfidence() {
        ParticipantClassification result = ParticipantClassifier.classify("XY9 Z");
        assertThat(result.type()).isEqualTo(ParticipantType.UNKNOWN);
        assertThat(result.confidence()).isEqualTo(0);
    }

    @Test
    void blankNameClassifiesAsUnknown() {
        assertThat(ParticipantClassifier.classify("").type()).isEqualTo(ParticipantType.UNKNOWN);
        assertThat(ParticipantClassifier.classify(null).type()).isEqualTo(ParticipantType.UNKNOWN);
    }
}
