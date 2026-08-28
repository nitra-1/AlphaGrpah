package com.alphagraph.api.admin;

import com.alphagraph.ownership.api.DiscoveredDealDetail;
import com.alphagraph.ownership.api.DiscoveryCandidate;
import com.alphagraph.ownership.deals.DealDetailReader;
import com.alphagraph.ownership.deals.DiscoveryReader;
import com.alphagraph.ownership.deals.DiscoveryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscoveryViewService {

    private final DiscoveryReader reader;
    private final DiscoveryService service;
    private final DealDetailReader dealDetailReader;

    public DiscoveryViewService(DiscoveryReader reader, DiscoveryService service, DealDetailReader dealDetailReader) {
        this.reader = reader;
        this.service = service;
        this.dealDetailReader = dealDetailReader;
    }

    public List<DiscoveryCandidateDto> listPendingReview() {
        return reader.findPendingReview().stream().map(DiscoveryViewService::toDto).toList();
    }

    public List<DiscoveredDealDetailDto> listDealsForSymbol(String symbol) {
        return dealDetailReader.findDealsForSymbol(symbol).stream().map(DiscoveryViewService::toDto).toList();
    }

    public boolean discard(String symbol) {
        return service.discard(symbol);
    }

    private static DiscoveryCandidateDto toDto(DiscoveryCandidate candidate) {
        return new DiscoveryCandidateDto(
            candidate.symbol(), candidate.securityName(), candidate.dealCount(), candidate.distinctBuyers(),
            candidate.totalQuantity(), candidate.firstDealDate(), candidate.latestDealDate(),
            candidate.maxMaterialityScore(), candidate.maxMaterialityLevel(), candidate.largestDealToAdtvRatio()
        );
    }

    private static DiscoveredDealDetailDto toDto(DiscoveredDealDetail deal) {
        return new DiscoveredDealDetailDto(
            deal.id(), deal.dealDate(), deal.clientName(), deal.buySell(), deal.quantity(), deal.price(),
            deal.dealValue(), deal.dealType(), deal.materialityScore(), deal.materialityLevel(),
            deal.dealToAdtvRatio(), deal.reportedFlowState()
        );
    }
}
