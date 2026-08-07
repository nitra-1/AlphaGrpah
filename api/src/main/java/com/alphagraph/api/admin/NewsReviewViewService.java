package com.alphagraph.api.admin;

import com.alphagraph.corporate.api.NewsReviewItem;
import com.alphagraph.corporate.newsfeed.NewsReviewReader;
import com.alphagraph.corporate.newsfeed.NewsReviewService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NewsReviewViewService {

    private final NewsReviewReader reader;
    private final NewsReviewService service;

    public NewsReviewViewService(NewsReviewReader reader, NewsReviewService service) {
        this.reader = reader;
        this.service = service;
    }

    public List<NewsReviewItemDto> listPendingReview() {
        return reader.findPendingReview().stream().map(NewsReviewViewService::toDto).toList();
    }

    public boolean keep(UUID id) {
        return service.keep(id);
    }

    public boolean discard(UUID id) {
        return service.discard(id);
    }

    private static NewsReviewItemDto toDto(NewsReviewItem item) {
        return new NewsReviewItemDto(item.id(), item.title(), item.source(), item.sourceUrl(), item.announcedAt(), item.extractedText());
    }
}
