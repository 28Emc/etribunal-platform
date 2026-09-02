package com.etribunal.core.cases;

import com.etribunal.core.analytics.AnalyticsService;
import com.etribunal.core.analytics.InteractionAction;
import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserCasesController {

    private final CaseService caseService;
    private final CurrentUserResolver currentUser;
    private final AnalyticsService analyticsService;

    public UserCasesController(CaseService caseService, CurrentUserResolver currentUser,
                               AnalyticsService analyticsService) {
        this.caseService = caseService;
        this.currentUser = currentUser;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/{username}/cases")
    public ResponseEntity<ApiResponse<List<CaseResponse>>> userCases(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int take,
            @RequestParam(required = false) String filter,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                caseService.getCasesByUsername(username, skip, take, filter, request)));
    }

    // POST /users/{userId}/track-share — visita de perfil via share (público, fire-and-forget)
    @PostMapping("/{userId}/track-share")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trackShare(
            @PathVariable UUID userId,
            HttpServletRequest request) {
        String utmSource = request.getParameter("utm_source");
        String utmMedium = request.getParameter("utm_medium");
        analyticsService.log(InteractionAction.VIEW.name(), null, userId, Map.of(
                "source", utmSource != null ? utmSource : "share",
                "medium", utmMedium != null ? utmMedium : "",
                "profile", true));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("tracked", true)));
    }
}