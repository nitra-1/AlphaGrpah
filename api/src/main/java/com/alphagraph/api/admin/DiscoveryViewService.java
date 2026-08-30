package com.alphagraph.api.admin;

import com.alphagraph.ownership.api.DiscoveredDealDetail;
import com.alphagraph.ownership.api.DiscoveryCandidate;
import com.alphagraph.ownership.api.InstitutionalInterpretationDetail;
import com.alphagraph.ownership.api.InterpretationReason;
import com.alphagraph.ownership.deals.DealDetailReader;
import com.alphagraph.ownership.deals.DiscoveryReader;
import com.alphagraph.ownership.deals.DiscoveryService;
import com.alphagraph.ownership.interpretation.InterpretationDetailReader;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DiscoveryViewService {

    private final DiscoveryReader reader;
    private final DiscoveryService service;
    private final DealDetailReader dealDetailReader;
    private final InterpretationDetailReader interpretationDetailReader;

    public DiscoveryViewService(
        DiscoveryReader reader, DiscoveryService service, DealDetailReader dealDetailReader,
        InterpretationDetailReader interpretationDetailReader
    ) {
        this.reader = reader;
        this.service = service;
        this.dealDetailReader = dealDetailReader;
        this.interpretationDetailReader = interpretationDetailReader;
    }

    public List<DiscoveryCandidateDto> listPendingReview() {
        return reader.findPendingReview().stream().map(DiscoveryViewService::toDto).toList();
    }

    public List<DiscoveredDealDetailDto> listDealsForSymbol(String symbol) {
        return dealDetailReader.findDealsForSymbol(symbol).stream().map(DiscoveryViewService::toDto).toList();
    }

    public Optional<InstitutionalInterpretationDetailDto> findInterpretation(String symbol) {
        return interpretationDetailReader.findLatest(symbol).map(DiscoveryViewService::toDto);
    }

    public boolean discard(String symbol) {
        return service.discard(symbol);
    }

    private static DiscoveryCandidateDto toDto(DiscoveryCandidate candidate) {
        return new DiscoveryCandidateDto(
            candidate.symbol(), candidate.securityName(), candidate.dealCount(), candidate.distinctBuyers(),
            candidate.distinctSellers(), candidate.totalQuantity(), candidate.firstDealDate(), candidate.latestDealDate(),
            candidate.maxMaterialityScore(), candidate.maxMaterialityLevel(), candidate.largestDealToAdtvRatio(),
            candidate.eventStructure(), candidate.institutionalState(), candidate.discoveryConfirmationState(),
            candidate.interpretationConfidence(), candidate.churnState(), candidate.confirmationSessionsElapsed(),
            candidate.confirmationFrozen(), candidate.interpretationReadiness()
        );
    }

    private static DiscoveredDealDetailDto toDto(DiscoveredDealDetail deal) {
        return new DiscoveredDealDetailDto(
            deal.id(), deal.dealDate(), deal.clientName(), deal.buySell(), deal.quantity(), deal.price(),
            deal.dealValue(), deal.dealType(), deal.isDuplicate(), deal.materialityScore(), deal.materialityLevel(),
            deal.dealToAdtvRatio(), deal.reportedFlowState()
        );
    }

    private static InstitutionalInterpretationDetailDto toDto(InstitutionalInterpretationDetail detail) {
        return new InstitutionalInterpretationDetailDto(
            detail.symbol(), detail.asOfDate(), detail.eventStructure(), detail.institutionalState(),
            detail.discoveryConfirmationState(), detail.confirmationFrozen(), detail.eventAnchorDate(),
            detail.confirmationSessionsElapsed(), detail.confirmationScore(), detail.priceConfirmationScore(),
            detail.deliveryConfirmationScore(), detail.volumeConfirmationScore(), detail.repeatActivityConfirmationScore(),
            detail.confirmationCoveragePct(), detail.confidence(), detail.materialityScore(), detail.reportedFlowState(),
            detail.churnState(), detail.institutionalBuyValue(), detail.institutionalSellValue(),
            detail.institutionalBuyerCount(), detail.institutionalSellerCount(), detail.interpretationReadiness(),
            detail.reasons().stream().map(DiscoveryViewService::toDto).toList()
        );
    }

    private static InterpretationReasonDto toDto(InterpretationReason reason) {
        return new InterpretationReasonDto(reason.reasonCode(), reason.metricValue(), reason.evidenceReference());
    }
}
