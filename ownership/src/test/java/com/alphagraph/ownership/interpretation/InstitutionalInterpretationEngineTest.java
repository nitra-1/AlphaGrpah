package com.alphagraph.ownership.interpretation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionalInterpretationEngineTest {

    private final InstitutionalInterpretationEngine engine = new InstitutionalInterpretationEngine();
    private static final LocalDate ANCHOR = LocalDate.of(2026, 8, 24);
    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 27);

    @Test
    void bothChurnEventStructuresMapToTheSameInstitutionalState() {
        assertThat(InstitutionalInterpretationEngine.institutionalStateFor(EventStructure.PROP_CHURN))
            .isEqualTo(InstitutionalState.HIGH_CHURN);
        assertThat(InstitutionalInterpretationEngine.institutionalStateFor(EventStructure.HIGH_CHURN_ACTIVITY))
            .isEqualTo(InstitutionalState.HIGH_CHURN);
    }

    private static ParticipantFlow flowWithConfidence(BigDecimal buyValue, double confidence) {
        return new ParticipantFlow(
            UUID.randomUUID(), "SBI Mutual Fund", ParticipantType.MUTUAL_FUND, confidence,
            buyValue, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, ChurnState.DIRECTIONAL, 1, 0, RepeatBehavior.ONE_OFF
        );
    }

    private static SymbolFlowSummary flowSummary(ParticipantFlow flow) {
        return new SymbolFlowSummary(
            flow.buyValue(), BigDecimal.ZERO, BigDecimal.ZERO, 0.0, ChurnState.DIRECTIONAL,
            flow.buyValue(), BigDecimal.ZERO, 1, 0, BigDecimal.ZERO, 0.0, 0.0, List.of(flow)
        );
    }

    private static InstitutionalInterpretationInput inputWith(SymbolFlowSummary flow, double materialityScore) {
        return new InstitutionalInterpretationInput(
            "TESTSYM", AS_OF, flow, EventStructure.INSTITUTIONAL_BUYING_CANDIDATE, InstitutionalState.POSSIBLE_ACCUMULATION,
            MaterialityLevel.HIGH, materialityScore, "NET_BUYING", List.of(), DiscoveryConfirmationResult.notApplicable(),
            List.of(), 1
        );
    }

    @Test
    void identicalInstitutionalValueAndCountWithDifferentParticipantConfidenceProducesDifferentConfidence() {
        // The direct regression test for the confidence-weighting gap: participant classification
        // confidence must actually move the final interpretation confidence.
        SymbolFlowSummary highConfidenceFlow = flowSummary(flowWithConfidence(new BigDecimal("50000000"), 95.0));
        SymbolFlowSummary lowConfidenceFlow = flowSummary(flowWithConfidence(new BigDecimal("50000000"), 60.0));

        double highConfidenceResult = engine.assemble(inputWith(highConfidenceFlow, 80.0)).confidence();
        double lowConfidenceResult = engine.assemble(inputWith(lowConfidenceFlow, 80.0)).confidence();

        assertThat(highConfidenceResult).isGreaterThan(lowConfidenceResult);
    }

    @Test
    void veryHighMaterialityProducesTheCorrespondingReasonCode() {
        SymbolFlowSummary flow = flowSummary(flowWithConfidence(new BigDecimal("50000000"), 95.0));
        InstitutionalInterpretationInput input = new InstitutionalInterpretationInput(
            "TESTSYM", AS_OF, flow, EventStructure.INSTITUTIONAL_BUYING_CANDIDATE, InstitutionalState.POSSIBLE_ACCUMULATION,
            MaterialityLevel.VERY_HIGH, 92.0, "NET_BUYING", List.of(), DiscoveryConfirmationResult.notApplicable(),
            List.of(), 1
        );

        InstitutionalInterpretationResult result = engine.assemble(input);

        assertThat(result.reasons()).extracting(ReasonCode::code).contains("VERY_HIGH_MATERIALITY");
    }

    @Test
    void aMediumPlusOppositeDirectionDealAfterTheAnchorProducesACountervailingReasonCode() {
        SymbolFlowSummary flow = flowSummary(flowWithConfidence(new BigDecimal("50000000"), 95.0));
        AnchorCandidateDeal countervailingSell = new AnchorCandidateDeal(
            UUID.randomUUID(), ANCHOR.plusDays(1), "SELL", MaterialityLevel.MEDIUM,
            new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000")
        );
        DiscoveryConfirmationResult confirmation = new DiscoveryConfirmationResult(
            DiscoveryConfirmationState.PENDING, false, ANCHOR, 1,
            new BigDecimal("50"), new BigDecimal("50"), null, null, new BigDecimal("20"), new BigDecimal("50")
        );
        InstitutionalInterpretationInput input = new InstitutionalInterpretationInput(
            "TESTSYM", AS_OF, flow, EventStructure.INSTITUTIONAL_BUYING_CANDIDATE, InstitutionalState.POSSIBLE_ACCUMULATION,
            MaterialityLevel.HIGH, 80.0, "NET_BUYING", List.of(countervailingSell), confirmation, List.of(), 1
        );

        InstitutionalInterpretationResult result = engine.assemble(input);

        assertThat(result.reasons()).extracting(ReasonCode::code).contains("COUNTERVAILING_SELL_DURING_ACCUMULATION");
    }

    @Test
    void notApplicableConfirmationNeverProducesConfirmationReasonCodes() {
        SymbolFlowSummary flow = flowSummary(flowWithConfidence(new BigDecimal("50000000"), 95.0));
        InstitutionalInterpretationInput input = new InstitutionalInterpretationInput(
            "TESTSYM", AS_OF, flow, EventStructure.PROP_CHURN, InstitutionalState.HIGH_CHURN,
            MaterialityLevel.HIGH, 80.0, "BALANCED", List.of(), DiscoveryConfirmationResult.notApplicable(), List.of(), 1
        );

        InstitutionalInterpretationResult result = engine.assemble(input);

        assertThat(result.reasons()).extracting(ReasonCode::code)
            .noneMatch(code -> code.contains("PRICE_") || code.contains("DELIVERY_") || code.contains("COUNTERVAILING"));
        assertThat(result.discoveryConfirmationState()).isEqualTo(DiscoveryConfirmationState.NOT_APPLICABLE);
    }
}
