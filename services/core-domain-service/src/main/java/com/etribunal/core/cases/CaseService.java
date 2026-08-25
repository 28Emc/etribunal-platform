package com.etribunal.core.cases;

import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.cases.dto.CreateCaseRequest;
import com.etribunal.core.security.CurrentUserResolver;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaseService {

    public static final String MASKED_USERNAME = "Anonymous Judge";
    public static final String MASKED_AVATAR =
            "https://secure.gravatar.com/avatar/0?d=mp&f=y";

    private final CaseRepository caseRepository;
    private final InternalUsersClient usersClient;
    private final CurrentUserResolver currentUser;

    public CaseService(CaseRepository caseRepository,
                       InternalUsersClient usersClient,
                       CurrentUserResolver currentUser) {
        this.caseRepository = caseRepository;
        this.usersClient = usersClient;
        this.currentUser = currentUser;
    }

    @Transactional
    public CaseResponse createCase(UUID authorId, CreateCaseRequest dto) {
        boolean isVote = dto.type() == CaseType.vote;

        UUID sideBId = null;
        if (dto.sideBUserId() != null && !dto.sideBUserId().isBlank()) {
            if (!isVote) {
                throw badRequest(
                        "Solo los casos de votacion pueden asignar un usuario como Side B");
            }
            sideBId = parseUuid(dto.sideBUserId(),
                    "El usuario seleccionado para Side B no existe");
            if (sideBId.equals(authorId)) {
                throw badRequest("No puedes asignarte a ti mismo como Side B");
            }
        }

        CaseEntity entity = new CaseEntity();
        entity.setType(dto.type());
        entity.setTitle(dto.title().trim());
        entity.setSideAContent(dto.sideAContent().trim());
        entity.setCategory(dto.category() != null && !dto.category().isBlank()
                ? dto.category() : "Other");
        entity.setStatus(isVote ? CaseStatus.WAITING : CaseStatus.PUBLIC);
        entity.setAnonymous(dto.anonymousOrDefault());
        entity.setInviteToken(isVote ? UUID.randomUUID().toString() : null);
        entity.setSideAUserId(authorId);
        entity.setSideBUserId(sideBId);
        entity.setSideASubtitle(dto.sideASubtitle());
        entity.setSideBSubtitle(dto.sideBSubtitle());
        entity.setBothWrongSubtitle(dto.bothWrongSubtitle());

        CaseEntity saved = caseRepository.save(entity);
        return toResponse(List.of(saved), null)
                .getFirst();
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> getCases(int skip, int take, String feedType,
                                       String category, String q,
                                       boolean createdByMe,
                                       HttpServletRequest request) {
        Optional<UUID> currentUserId = currentUser.currentUserId(request);
        List<UUID> followingIds = null;

        boolean followingFeed = "following".equalsIgnoreCase(feedType)
                && currentUserId.isPresent();
        if (followingFeed) {
            followingIds = usersClient.followingIds(currentUserId.get());
        }

        Specification<CaseEntity> spec = CaseSpecifications.feed(
                q, category, followingIds,
                currentUserId.orElse(null), createdByMe);
        Pageable pageable = CaseSpecifications.pageable(skip, take,
                "trending".equalsIgnoreCase(feedType));

        return toResponse(caseRepository.findAll(spec, pageable).getContent(),
                currentUserId.orElse(null));
    }

    @Transactional(readOnly = true)
    public CaseResponse getCase(UUID id, HttpServletRequest request) {
        Optional<UUID> currentUserId = currentUser.currentUserId(request);

        CaseEntity entity = caseRepository.findById(id)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));

        return toResponse(List.of(entity), currentUserId.orElse(null))
                .getFirst();
    }

    private List<CaseResponse> toResponse(List<CaseEntity> cases, UUID requesterId) {
        LinkedHashSet<UUID> userIds = new LinkedHashSet<>();
        for (CaseEntity c : cases) {
            userIds.add(c.getSideAUserId());
            if (c.getSideBUserId() != null) {
                userIds.add(c.getSideBUserId());
            }
        }

        Map<UUID, UserSummary> summaries = new HashMap<>();
        if (!userIds.isEmpty()) {
            summaries.putAll(usersClient.summaries(List.copyOf(userIds)).stream()
                    .collect(Collectors.toMap(UserSummary::id, Function.identity())));
        }

        List<CaseResponse> responses = new ArrayList<>(cases.size());
        for (CaseEntity c : cases) {
            responses.add(new CaseResponse(
                    c.getId(),
                    c.getType().name(),
                    c.getStatus().name(),
                    c.getCategory(),
                    c.getTitle(),
                    c.getSideAContent(),
                    c.getSideBContent(),
                    c.getSideASubtitle(),
                    c.getSideBSubtitle(),
                    c.getBothWrongSubtitle(),
                    c.getContentLanguage(),
                    c.isAnonymous(),
                    c.isPrivate(),
                    c.getCreatedAt(),
                    c.getUpdatedAt(),
                    c.getSideAUserId(),
                    c.getSideBUserId(),
                    toMaskedDto(summaries.get(c.getSideAUserId()), requesterId),
                    c.getSideBUserId() != null
                            ? toMaskedDto(summaries.get(c.getSideBUserId()), requesterId)
                            : null,
                    c.getTotalVotes(),
                    c.getVotesA(),
                    c.getVotesB(),
                    c.getVotesBothWrong(),
                    c.getTotalComments(),
                    c.getTotalViews(),
                    c.getTotalShares(),
                    c.getTotalAnchors(),
                    c.getModerationStatus().name(),
                    false,
                    false));
        }
        return responses;
    }

    /**
     * Réplica del maskUser del monolito: si el usuario es anónimo y no es el
     * propio requester, se oculta identidad.
     */
    private static CaseResponse.UserDto toMaskedDto(UserSummary summary,
                                                    UUID requesterId) {
        if (summary == null) {
            return new CaseResponse.UserDto(null, "Unknown", null, true);
        }
        boolean self = summary.id() != null && summary.id().equals(requesterId);
        if (summary.anonymous() && !self) {
            return new CaseResponse.UserDto(summary.id(), MASKED_USERNAME,
                    MASKED_AVATAR, true);
        }
        return CaseResponse.toUserDto(summary);
    }

    private static UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw badRequest(message);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
