package com.etribunal.core.cases;

import com.etribunal.core.api.ApiResponse;
import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.cases.dto.CreateCaseRequest;
import com.etribunal.core.cases.dto.RespondSideBRequest;
import com.etribunal.core.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cases")
public class CasesController {

    private final CaseService caseService;
    private final CurrentUserResolver currentUser;

    public CasesController(CaseService caseService, CurrentUserResolver currentUser) {
        this.caseService = caseService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CaseResponse>> create(
            @Valid @RequestBody CreateCaseRequest dto,
            HttpServletRequest request) {
        CaseResponse created =
                caseService.createCase(currentUser.requiredUserId(request), dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CaseResponse>>> feed(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int take,
            @RequestParam(defaultValue = "for_you") String feedType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, name = "createdBy") String createdBy,
            HttpServletRequest request) {
        List<CaseResponse> cases = caseService.getCases(skip, take, feedType, category,
                q, createdBy != null && createdBy.equals("me"), request);
        return ResponseEntity.ok(ApiResponse.ok(cases));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CaseResponse>> detail(
            @PathVariable UUID id,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(caseService.getCase(id, request)));
    }

    @GetMapping("/invite/{token}")
    public ResponseEntity<ApiResponse<CaseResponse>> byInviteToken(
            @PathVariable String token,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(caseService.getCaseByInviteToken(token, request)));
    }

    @PostMapping("/respond")
    public ResponseEntity<ApiResponse<CaseResponse>> respondSideB(
            @Valid @RequestBody RespondSideBRequest dto,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                caseService.respondAsSideB(currentUser.requiredUserId(request), dto)));
    }

    @PostMapping("/{id}/invite-link")
    public ResponseEntity<ApiResponse<CaseService.InviteLinkResponse>> inviteLink(
            @PathVariable UUID id,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                caseService.getOrRegenerateInviteLink(
                        currentUser.requiredUserId(request), id)));
    }
}
