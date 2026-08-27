package com.etribunal.core.search;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService searchService;
    private final CurrentUserResolver currentUser;

    public SearchController(SearchService searchService,
                            CurrentUserResolver currentUser) {
        this.searchService = searchService;
        this.currentUser = currentUser;
    }

    /**
     * GET /cases/search?q=...&skip=0&take=8
     * Full-text search using PostgreSQL tsvector + ts_rank.
     * Requires authentication.
     */
    @GetMapping("/cases/search")
    public ResponseEntity<ApiResponse<List<SearchService.SearchResult>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "8") int take,
            HttpServletRequest request) {
        UUID userId = currentUser.requiredUserId(request);
        List<SearchService.SearchResult> results = searchService.search(q, skip, take, userId);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
