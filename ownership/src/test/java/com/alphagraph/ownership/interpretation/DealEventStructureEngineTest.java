package com.alphagraph.ownership.interpretation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DealEventStructureEngineTest {

    private final DealEventStructureEngine engine = new DealEventStructureEngine();

    private static SymbolFlowSummary churnSummary(double propShare, double propConfidence) {
        return new SymbolFlowSummary(
            new BigDecimal("100000000"), new BigDecimal("98000000"), new BigDecimal("196000000"),
            0.99, ChurnState.VERY_HIGH_CHURN,
            BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
            new BigDecimal("196000000").multiply(BigDecimal.valueOf(propShare)), propShare, propConfidence,
            List.of()
        );
    }

    @Test
    void highChurnWithOnlyCorporateOrUnknownParticipantsLandsOnHighChurnActivityNotPropChurn() {
        // The direct regression test for the BUY-classification-alone gap: churn must be judged
        // from actual round-trip behavior first, and never escalate to PROP_CHURN just because
        // participants happen to classify as CORPORATE/UNKNOWN.
        SymbolFlowSummary flow = churnSummary(0.0, 0.0);

        EventStructure result = engine.decide(flow, MaterialityLevel.HIGH);

        assertThat(result).isEqualTo(EventStructure.HIGH_CHURN_ACTIVITY);
    }

    @Test
    void highChurnWithConfidentDominantPropConcentrationEscalatesToPropChurn() {
        SymbolFlowSummary flow = churnSummary(0.75, 70.0);

        EventStructure result = engine.decide(flow, MaterialityLevel.HIGH);

        assertThat(result).isEqualTo(EventStructure.PROP_CHURN);
    }

    @Test
    void highChurnWithDominantButLowConfidenceProbablyPropDoesNotEscalate() {
        // Prop-type participants dominate the churned value, but the classification itself is
        // low-confidence (e.g. an ambiguous name-pattern guess) - must not manufacture PROP_CHURN.
        SymbolFlowSummary flow = churnSummary(0.75, 45.0);

        EventStructure result = engine.decide(flow, MaterialityLevel.HIGH);

        assertThat(result).isEqualTo(EventStructure.HIGH_CHURN_ACTIVITY);
    }

    @Test
    void multipleInstitutionalBuyersWithHighMaterialityIsMultiInstitutionBuying() {
        SymbolFlowSummary flow = new SymbolFlowSummary(
            new BigDecimal("100000000"), BigDecimal.ZERO, BigDecimal.ZERO, 0.0, ChurnState.DIRECTIONAL,
            new BigDecimal("100000000"), BigDecimal.ZERO, 3, 0,
            BigDecimal.ZERO, 0.0, 0.0, List.of()
        );

        EventStructure result = engine.decide(flow, MaterialityLevel.HIGH);

        assertThat(result).isEqualTo(EventStructure.MULTI_INSTITUTION_BUYING);
    }

    @Test
    void noActivityAtAllIsUnresolved() {
        SymbolFlowSummary flow = new SymbolFlowSummary(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, ChurnState.DIRECTIONAL,
            BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, 0.0, 0.0, List.of()
        );

        EventStructure result = engine.decide(flow, MaterialityLevel.LOW);

        assertThat(result).isEqualTo(EventStructure.UNRESOLVED);
    }
}
