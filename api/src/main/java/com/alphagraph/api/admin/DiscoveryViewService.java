package com.alphagraph.api.admin;

import com.alphagraph.ownership.api.DiscoveryCandidate;
import com.alphagraph.ownership.deals.DiscoveryReader;
import com.alphagraph.ownership.deals.DiscoveryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscoveryViewService {

    private final DiscoveryReader reader;
    private final DiscoveryService service;

    public DiscoveryViewService(DiscoveryReader reader, DiscoveryService service) {
        this.reader = reader;
        this.service = service;
    }

    public List<DiscoveryCandidateDto> listPendingReview() {
        return reader.findPendingReview().stream().map(DiscoveryViewService::toDto).toList();
    }

    public boolean discard(String symbol) {
        return service.discard(symbol);
    }

    private static DiscoveryCandidateDto toDto(DiscoveryCandidate candidate) {
        return new DiscoveryCandidateDto(
            candidate.symbol(), candidate.securityName(), candidate.dealCount(), candidate.distinctBuyers(),
            candidate.totalQuantity(), candidate.firstDealDate(), candidate.latestDealDate()
        );
    }
}
