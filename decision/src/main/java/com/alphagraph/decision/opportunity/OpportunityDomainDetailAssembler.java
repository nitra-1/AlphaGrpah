package com.alphagraph.decision.opportunity;

import com.alphagraph.decision.api.DomainContribution;
import com.alphagraph.decision.api.OpportunityDomainDetail;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Assembles the full Stage 1-3 causal-chain bundle for all 5 positive domains, anchored to the
 * same {@code asOfDate} as the displayed Stage 4 convergence snapshot - never {@code
 * LocalDate.now()}. Always returns exactly 5 entries (matching {@code
 * convergence_domain_contributions}'s own "always exactly the 5 positive domains" convention),
 * with empty evidence/sequence lists and a null inflection where a domain genuinely has nothing as
 * of that date, never omitted.
 */
@Component
class OpportunityDomainDetailAssembler {

    private final FinancialOpportunityReader financialReader;
    private final OwnershipOpportunityReader ownershipReader;
    private final MarketOpportunityReader marketReader;
    private final SectorOpportunityReader sectorReader;
    private final CapitalAllocationOpportunityReader capitalAllocationReader;

    OpportunityDomainDetailAssembler(
        FinancialOpportunityReader financialReader, OwnershipOpportunityReader ownershipReader,
        MarketOpportunityReader marketReader, SectorOpportunityReader sectorReader,
        CapitalAllocationOpportunityReader capitalAllocationReader
    ) {
        this.financialReader = financialReader;
        this.ownershipReader = ownershipReader;
        this.marketReader = marketReader;
        this.sectorReader = sectorReader;
        this.capitalAllocationReader = capitalAllocationReader;
    }

    List<OpportunityDomainDetail> assemble(UUID instrumentId, LocalDate asOfDate, List<DomainContribution> convergenceContributions) {
        Map<String, DomainContribution> contributionsByDomain = convergenceContributions.stream()
            .collect(java.util.stream.Collectors.toMap(DomainContribution::domain, Function.identity()));

        return List.of(
            oneDomain("FINANCIAL", instrumentId, asOfDate, contributionsByDomain,
                financialReader::findLatestEvidence, financialReader::findLatestInflection, financialReader::findActiveSequences),
            oneDomain("OWNERSHIP", instrumentId, asOfDate, contributionsByDomain,
                ownershipReader::findLatestEvidence, ownershipReader::findLatestInflection, ownershipReader::findActiveSequences),
            oneDomain("MARKET", instrumentId, asOfDate, contributionsByDomain,
                marketReader::findLatestEvidence, marketReader::findLatestInflection, marketReader::findActiveSequences),
            oneDomain("SECTOR", instrumentId, asOfDate, contributionsByDomain,
                sectorReader::findLatestEvidence, sectorReader::findLatestInflection, sectorReader::findActiveSequences),
            oneDomain("CAPITAL_ALLOCATION", instrumentId, asOfDate, contributionsByDomain,
                capitalAllocationReader::findLatestEvidence, capitalAllocationReader::findLatestInflection, capitalAllocationReader::findActiveSequences)
        );
    }

    private OpportunityDomainDetail oneDomain(
        String domain, UUID instrumentId, LocalDate asOfDate, Map<String, DomainContribution> contributionsByDomain,
        java.util.function.BiFunction<UUID, LocalDate, List<com.alphagraph.decision.api.EvidenceObservation>> evidenceFn,
        java.util.function.BiFunction<UUID, LocalDate, java.util.Optional<com.alphagraph.decision.api.InflectionState>> inflectionFn,
        java.util.function.BiFunction<UUID, LocalDate, List<com.alphagraph.decision.api.TransformationSequence>> sequencesFn
    ) {
        return new OpportunityDomainDetail(
            domain,
            evidenceFn.apply(instrumentId, asOfDate),
            inflectionFn.apply(instrumentId, asOfDate).orElse(null),
            sequencesFn.apply(instrumentId, asOfDate),
            contributionsByDomain.get(domain)
        );
    }
}
