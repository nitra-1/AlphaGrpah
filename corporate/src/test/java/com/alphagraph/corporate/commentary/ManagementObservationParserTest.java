package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.CommitmentLevel;
import com.alphagraph.corporate.api.DocumentFact;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.ManagementObservation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementObservationParserTest {

    private final ManagementObservationParser parser = new ManagementObservationParser();
    private final UUID documentId = UUID.randomUUID();
    private final UUID instrumentId = UUID.randomUUID();
    private final Instant announcedAt = Instant.now();

    @Test
    void reassemblesOneGroupIntoOneObservation() {
        UUID group = UUID.randomUUID();
        List<DocumentFact> facts = List.of(
            fact("metrictype", "REVENUE_GUIDANCE", "HIGH", group),
            fact("guidancevalue", "30%", null, group),
            fact("guidancevaluenumeric", "30", null, group),
            fact("guidanceperiod", "next two years", null, group),
            fact("direction", "POSITIVE", null, group),
            fact("signal", "Growth Visibility", null, group)
        );

        List<ManagementObservation> observations = parser.parse(documentId, instrumentId, "TCS", announcedAt, facts);

        assertThat(observations).hasSize(1);
        ManagementObservation observation = observations.get(0);
        assertThat(observation.metricType()).isEqualTo("REVENUE_GUIDANCE");
        assertThat(observation.guidanceValue()).isEqualTo("30%");
        assertThat(observation.guidanceValueNumeric()).isEqualTo(30.0);
        assertThat(observation.guidancePeriod()).isEqualTo("next two years");
        assertThat(observation.direction()).isEqualTo(GuidanceDirection.POSITIVE);
        assertThat(observation.signal()).isEqualTo("Growth Visibility");
        assertThat(observation.commitmentLevel()).isEqualTo(CommitmentLevel.HIGH);
    }

    @Test
    void separatesTwoGroupsIntoTwoObservations() {
        UUID groupA = UUID.randomUUID();
        UUID groupB = UUID.randomUUID();
        List<DocumentFact> facts = List.of(
            fact("metrictype", "REVENUE_GUIDANCE", "HIGH", groupA),
            fact("direction", "POSITIVE", null, groupA),
            fact("metrictype", "MARGIN_GUIDANCE", "MEDIUM", groupB),
            fact("direction", "NEUTRAL", null, groupB)
        );

        List<ManagementObservation> observations = parser.parse(documentId, instrumentId, "TCS", announcedAt, facts);

        assertThat(observations).hasSize(2);
        assertThat(observations).extracting(ManagementObservation::metricType)
            .containsExactlyInAnyOrder("REVENUE_GUIDANCE", "MARGIN_GUIDANCE");
    }

    @Test
    void factsWithoutAGroupAreIgnored() {
        List<DocumentFact> facts = List.of(fact("metrictype", "REVENUE_GUIDANCE", "HIGH", null));

        assertThat(parser.parse(documentId, instrumentId, "TCS", announcedAt, facts)).isEmpty();
    }

    @Test
    void groupMissingRequiredFieldsIsSkipped() {
        UUID group = UUID.randomUUID();
        // No "direction" fact in this group.
        List<DocumentFact> facts = List.of(fact("metrictype", "REVENUE_GUIDANCE", "HIGH", group));

        assertThat(parser.parse(documentId, instrumentId, "TCS", announcedAt, facts)).isEmpty();
    }

    @Test
    void qualitativeObservationHasNullNumericValue() {
        UUID group = UUID.randomUUID();
        List<DocumentFact> facts = List.of(
            fact("metrictype", "DEMAND", "MEDIUM", group),
            fact("guidancevalue", "strong domestic demand", null, group),
            fact("direction", "POSITIVE", null, group)
        );

        ManagementObservation observation = parser.parse(documentId, instrumentId, "TCS", announcedAt, facts).get(0);

        assertThat(observation.guidanceValueNumeric()).isNull();
    }

    private DocumentFact fact(String type, String value, String commitmentLevel, UUID group) {
        return new DocumentFact(UUID.randomUUID(), documentId, type, value, "", 90.0, Instant.now(), commitmentLevel, group);
    }
}
