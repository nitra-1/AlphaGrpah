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
    void sovereignAuthorityKeywordClassifiesAsSovereignPensionFund() {
        // Real production name from the LENSKART investigation - "AUTHORITY", not "SOVEREIGN".
        ParticipantClassification result = ParticipantClassifier.classify("KUWAIT INVESTMENT AUTHORITY");
        assertThat(result.type()).isEqualTo(ParticipantType.SOVEREIGN_PENSION_FUND);
        assertThat(result.confidence()).isEqualTo(60);
    }

    @Test
    void retirementKeywordClassifiesAsSovereignPensionFund() {
        ParticipantClassification result = ParticipantClassifier.classify("TEACHERS RETIREMENT SYSTEM OF THE STATE OF ILLINOIS");
        assertThat(result.type()).isEqualTo(ParticipantType.SOVEREIGN_PENSION_FUND);
    }

    @Test
    void superannuationKeywordClassifiesAsSovereignPensionFund() {
        ParticipantClassification result = ParticipantClassifier.classify("RETAIL EMPLOYEES SUPERANNUATION TRUST");
        assertThat(result.type()).isEqualTo(ParticipantType.SOVEREIGN_PENSION_FUND);
    }

    @Test
    void bareFundKeywordClassifiesAsFpiFiiAtLowerConfidenceThanForeignStructure() {
        // No legal suffix, no explicit FPI/FII marker - just "FUND" on its own, a weaker signal.
        ParticipantClassification result = ParticipantClassifier.classify("GHISALLO MASTER FUND LP");
        assertThat(result.type()).isEqualTo(ParticipantType.FPI_FII);
        assertThat(result.confidence()).isEqualTo(55);
    }

    @Test
    void ventureFundFamilyClassifiesConsistentlyRegardlessOfTokenCount() {
        // Real production pair that previously diverged (INDIVIDUAL/70 vs UNKNOWN/0) purely
        // because the old personal-name-shape fallback's token-count regex matched one and not
        // the other. Both now resolve identically via the bare VENTURES keyword.
        ParticipantClassification shortName = ParticipantClassifier.classify("ALPHA WAVE VENTURES");
        ParticipantClassification longName = ParticipantClassifier.classify("ALPHA WAVE VENTURES II LP");
        assertThat(shortName.type()).isEqualTo(ParticipantType.FPI_FII);
        assertThat(longName.type()).isEqualTo(ParticipantType.FPI_FII);
        assertThat(shortName.confidence()).isEqualTo(longName.confidence());
    }

    @Test
    void bankKeywordClassifiesAsBroker() {
        ParticipantClassification result = ParticipantClassifier.classify("GOLDMAN SACHS BANK EUROPE SE");
        assertThat(result.type()).isEqualTo(ParticipantType.BROKER);
        assertThat(result.confidence()).isEqualTo(70);
    }

    @Test
    void securitiesKeywordClassifiesAsBroker() {
        ParticipantClassification result = ParticipantClassifier.classify("BOFA SECURITIES EUROPE SA");
        assertThat(result.type()).isEqualTo(ParticipantType.BROKER);
    }

    @Test
    void denylistedWordBlocksThePersonalNameFallbackEvenWhenTheShapeMatches() {
        // Four all-caps tokens, no keyword or legal suffix caught earlier - would match the
        // personal-name shape, but "TRUST"/"HOLDINGS"/"GLOBAL"/"ASSET" are institution-shaped
        // words, so this must not be guessed as an individual.
        ParticipantClassification result = ParticipantClassifier.classify("GLOBAL ASSET HOLDINGS TRUST");
        assertThat(result.type()).isEqualTo(ParticipantType.UNKNOWN);
        assertThat(result.confidence()).isEqualTo(0);
    }

    @Test
    void twoTokenInstitutionNameWithNoKeywordOrSuffixFallsToUnknownNotIndividual() {
        // Real production name, disclosed residual gap - no keyword, no legal suffix, and only
        // two tokens (the personal-name fallback now requires 3+), so this correctly refuses to
        // guess rather than confidently mislabeling a bank as an individual.
        ParticipantClassification result = ParticipantClassifier.classify("SOCIETE GENERALE");
        assertThat(result.type()).isEqualTo(ParticipantType.UNKNOWN);
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
