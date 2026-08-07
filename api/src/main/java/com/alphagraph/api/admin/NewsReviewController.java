package com.alphagraph.api.admin;

import com.alphagraph.api.error.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Module 2.6 pre-filter retrofit: news articles {@code corporate.newsfeed.NewsRelevanceFilter}
 * couldn't match to any tracked instrument land here instead of being silently discarded or
 * auto-extracted - an admin reviews them whenever they get to it (not necessarily in real time)
 * and either promotes one to real (billed) Claude extraction or discards it permanently.
 * ADMIN-only throughout (including the list view), matching every other mutation endpoint's
 * convention in this project.
 */
@RestController
@RequestMapping("/api/v1/admin/news-review")
@PreAuthorize("hasRole('ADMIN')")
public class NewsReviewController {

    private final NewsReviewViewService viewService;

    public NewsReviewController(NewsReviewViewService viewService) {
        this.viewService = viewService;
    }

    @Operation(summary = "List filtered-out news awaiting admin review")
    @GetMapping
    public List<NewsReviewItemDto> list() {
        return viewService.listPendingReview();
    }

    @Operation(summary = "Keep an article", description = "Triggers real Claude extraction immediately, not queued for the next scheduled run.")
    @PostMapping("/{id}/keep")
    public ResponseEntity<Void> keep(@PathVariable UUID id) {
        if (!viewService.keep(id)) {
            throw new NotFoundException("No pending-review article with id " + id);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Discard an article", description = "Terminal - the article is never extracted.")
    @PostMapping("/{id}/discard")
    public ResponseEntity<Void> discard(@PathVariable UUID id) {
        if (!viewService.discard(id)) {
            throw new NotFoundException("No pending-review article with id " + id);
        }
        return ResponseEntity.noContent().build();
    }
}
