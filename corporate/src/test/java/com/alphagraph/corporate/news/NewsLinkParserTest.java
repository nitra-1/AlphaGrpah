package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.DocumentFact;
import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsLinkParserTest {

    private final NewsInstrumentMatcher matcher = mock(NewsInstrumentMatcher.class);
    private final NewsLinkParser parser = new NewsLinkParser(matcher);
    private final UUID documentId = UUID.randomUUID();
    private final Instant announcedAt = Instant.now();

    @Test
    void reassemblesOneGroupIntoOneLinkWhenCompanyResolves() {
        UUID group = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        when(matcher.resolve("Kaynes Technology")).thenReturn(Optional.of(new MatchedInstrument(instrumentId, "KAYNES")));

        List<DocumentFact> facts = List.of(
            fact("companyname", "Kaynes Technology", group),
            fact("direction", "POSITIVE", group),
            fact("signal", "PLI Beneficiary", group),
            fact("impactsummary", "Direct beneficiary of the PLI scheme.", group)
        );

        List<NewsInstrumentLink> links = parser.parse(documentId, announcedAt, facts);

        assertThat(links).hasSize(1);
        NewsInstrumentLink link = links.get(0);
        assertThat(link.instrumentId()).isEqualTo(instrumentId);
        assertThat(link.symbol()).isEqualTo("KAYNES");
        assertThat(link.direction()).isEqualTo(NewsImpactDirection.POSITIVE);
        assertThat(link.signal()).isEqualTo("PLI Beneficiary");
        assertThat(link.impactSummary()).isEqualTo("Direct beneficiary of the PLI scheme.");
    }

    @Test
    void unresolvedCompanyProducesNoLink() {
        UUID group = UUID.randomUUID();
        when(matcher.resolve(any())).thenReturn(Optional.empty());

        List<DocumentFact> facts = List.of(
            fact("companyname", "Some Untracked Company", group),
            fact("direction", "POSITIVE", group)
        );

        assertThat(parser.parse(documentId, announcedAt, facts)).isEmpty();
    }

    @Test
    void groupMissingDirectionIsSkipped() {
        UUID group = UUID.randomUUID();
        List<DocumentFact> facts = List.of(fact("companyname", "TCS", group));

        assertThat(parser.parse(documentId, announcedAt, facts)).isEmpty();
    }

    @Test
    void factsWithoutAGroupAreIgnored() {
        List<DocumentFact> facts = List.of(fact("companyname", "TCS", null));

        assertThat(parser.parse(documentId, announcedAt, facts)).isEmpty();
    }

    @Test
    void separatesTwoGroupsIntoTwoLinksWhenBothResolve() {
        UUID groupA = UUID.randomUUID();
        UUID groupB = UUID.randomUUID();
        when(matcher.resolve("Kaynes Technology")).thenReturn(Optional.of(new MatchedInstrument(UUID.randomUUID(), "KAYNES")));
        when(matcher.resolve("Dixon Technologies")).thenReturn(Optional.of(new MatchedInstrument(UUID.randomUUID(), "DIXON")));

        List<DocumentFact> facts = List.of(
            fact("companyname", "Kaynes Technology", groupA), fact("direction", "POSITIVE", groupA),
            fact("companyname", "Dixon Technologies", groupB), fact("direction", "POSITIVE", groupB)
        );

        assertThat(parser.parse(documentId, announcedAt, facts)).hasSize(2);
    }

    private DocumentFact fact(String type, String value, UUID group) {
        return new DocumentFact(UUID.randomUUID(), documentId, type, value, "", 90.0, Instant.now(), null, group);
    }
}
