package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.DocumentFact;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EconomicEventParserTest {

    private final EconomicEventParser parser = new EconomicEventParser();
    private final UUID documentId = UUID.randomUUID();

    private DocumentFact fact(String type, String value, double confidence, UUID group) {
        return new DocumentFact(UUID.randomUUID(), documentId, type, value, "", confidence, Instant.now(), null, group);
    }

    @Test
    void reassemblesEventLevelGroupByItsDistinguishingKey() {
        UUID eventGroup = UUID.randomUUID();
        List<DocumentFact> facts = List.of(
            fact("economicrelevance", "ECONOMIC", 88, eventGroup),
            fact("theme", "DEFENCE_SPENDING", 88, eventGroup),
            fact("eventdirection", "POSITIVE", 88, eventGroup),
            fact("magnitude", "HIGH", 88, eventGroup),
            fact("horizon", "MEDIUM_TERM", 88, eventGroup)
        );

        ParsedNewsDocument result = parser.parse(documentId, facts);

        assertThat(result.event()).isNotNull();
        assertThat(result.event().economicRelevance()).isEqualTo("ECONOMIC");
        assertThat(result.event().theme()).isEqualTo("DEFENCE_SPENDING");
        assertThat(result.event().direction()).isEqualTo("POSITIVE");
        assertThat(result.event().magnitude()).isEqualTo("HIGH");
        assertThat(result.event().confidence()).isEqualTo(88.0);
        assertThat(result.event().horizon()).isEqualTo("MEDIUM_TERM");
        assertThat(result.sectorImpacts()).isEmpty();
        assertThat(result.companyImpacts()).isEmpty();
    }

    @Test
    void oneDocumentCanProduceMultipleSectorImpactGroupsWithDifferentDirections() {
        UUID airlinesGroup = UUID.randomUUID();
        UUID oilProducersGroup = UUID.randomUUID();
        List<DocumentFact> facts = List.of(
            fact("sectorname", "Airlines", 85, airlinesGroup),
            fact("sectordirection", "NEGATIVE", 85, airlinesGroup),
            fact("sectorstrength", "HIGH", 85, airlinesGroup),
            fact("mechanism", "higher fuel cost", 85, airlinesGroup),
            fact("sectorname", "Oil Producers", 85, oilProducersGroup),
            fact("sectordirection", "POSITIVE", 85, oilProducersGroup),
            fact("sectorstrength", "HIGH", 85, oilProducersGroup)
        );

        ParsedNewsDocument result = parser.parse(documentId, facts);

        assertThat(result.sectorImpacts()).hasSize(2);
        assertThat(result.sectorImpacts()).extracting(ParsedSectorImpact::sector, ParsedSectorImpact::direction)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("Airlines", "NEGATIVE"),
                org.assertj.core.groups.Tuple.tuple("Oil Producers", "POSITIVE")
            );
    }

    @Test
    void sectorImpactGroupMissingRequiredFieldIsSkipped() {
        UUID group = UUID.randomUUID();
        List<DocumentFact> facts = List.of(
            fact("sectorname", "Airlines", 85, group)
            // no sectordirection/sectorstrength facts in this group
        );

        ParsedNewsDocument result = parser.parse(documentId, facts);

        assertThat(result.sectorImpacts()).isEmpty();
    }

    @Test
    void multipleCompanyImpactGroupsAreAllReassembled() {
        UUID companyAGroup = UUID.randomUUID();
        UUID companyBGroup = UUID.randomUUID();
        List<DocumentFact> facts = List.of(
            fact("companyname", "Kaynes Technology", 90, companyAGroup),
            fact("direction", "POSITIVE", 90, companyAGroup),
            fact("sector", "Electronics", 90, companyAGroup),
            fact("exposuretype", "REGULATORY", 90, companyAGroup),
            fact("companyname", "Dixon Technologies", 85, companyBGroup),
            fact("direction", "POSITIVE", 85, companyBGroup)
        );

        ParsedNewsDocument result = parser.parse(documentId, facts);

        assertThat(result.companyImpacts()).hasSize(2);
        assertThat(result.companyImpacts()).extracting(ParsedCompanyImpact::companyName)
            .containsExactlyInAnyOrder("Kaynes Technology", "Dixon Technologies");
        ParsedCompanyImpact kaynes = result.companyImpacts().stream().filter(c -> c.companyName().equals("Kaynes Technology")).findFirst().orElseThrow();
        assertThat(kaynes.sector()).isEqualTo("Electronics");
        assertThat(kaynes.exposureType()).isEqualTo("REGULATORY");
    }

    @Test
    void companyImpactGroupMissingDirectionIsSkipped() {
        UUID group = UUID.randomUUID();
        List<DocumentFact> facts = List.of(fact("companyname", "Kaynes Technology", 90, group));

        ParsedNewsDocument result = parser.parse(documentId, facts);

        assertThat(result.companyImpacts()).isEmpty();
    }

    @Test
    void factsWithNoGroupAreIgnored() {
        List<DocumentFact> facts = List.of(
            new DocumentFact(UUID.randomUUID(), documentId, "economicrelevance", "ECONOMIC", "", 90, Instant.now(), null, null)
        );

        ParsedNewsDocument result = parser.parse(documentId, facts);

        assertThat(result.event()).isNull();
    }

    @Test
    void noFactsAtAllProducesANullEventAndEmptyLists() {
        ParsedNewsDocument result = parser.parse(documentId, List.of());

        assertThat(result.event()).isNull();
        assertThat(result.sectorImpacts()).isEmpty();
        assertThat(result.companyImpacts()).isEmpty();
    }
}
