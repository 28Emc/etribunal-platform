package com.etribunal.core.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.cases.dto.CaseResponse;
import com.etribunal.core.cases.dto.CreateCaseRequest;
import com.etribunal.core.cases.dto.RespondSideBRequest;
import com.etribunal.core.config.FrontendUrlProperties;
import com.etribunal.core.moderation.ModerationService;
import com.etribunal.core.reactions.ReactionRepository;
import com.etribunal.core.reactions.ReactionTarget;
import com.etribunal.core.saved.CaseShareRepository;
import com.etribunal.core.saved.SavedCaseRepository;
import com.etribunal.core.security.CurrentUserResolver;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
import com.etribunal.core.votes.VoteRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private InternalUsersClient usersClient;

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private SavedCaseRepository savedCaseRepository;

    @Mock
    private CaseShareRepository caseShareRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private ModerationService moderationService;

    private CaseService caseService;

    private final UUID authorId = UUID.randomUUID();
    private final UUID sideBId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        caseService = new CaseService(caseRepository, usersClient, currentUserResolver,
                new FrontendUrlProperties("http://localhost:3000/"),
                savedCaseRepository, caseShareRepository, voteRepository, reactionRepository,
                moderationService);
        lenient().when(currentUserResolver.currentUserId(request))
                .thenReturn(Optional.of(authorId));
        lenient().when(usersClient.summaries(anyList())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> new UserSummary(id, "user_" + id.toString().substring(0, 4),
                            "https://example.com/a.png", false))
                    .toList();
        });
        // Batch enrichment mocks
        lenient().when(savedCaseRepository.findCaseIdsByUserIdAndCaseIdIn(any(), anyList()))
                .thenReturn(List.of());
        lenient().when(caseShareRepository.findCaseIdsByUserIdAndCaseIdIn(any(), anyList()))
                .thenReturn(List.of());
        lenient().when(voteRepository.findByUserIdAndCaseIdIn(any(), anyList()))
                .thenReturn(List.of());
        lenient().when(reactionRepository.findEmojiByTargetTypeAndTargetIdInAndUserId(
                any(), anyList(), any())).thenReturn(List.of());
    }

    @Test
    void createClassicCaseIsPublicWithoutInvite() {
        when(caseRepository.save(any()))
                .thenAnswer(inv -> withUuid(inv.getArgument(0)));

        CaseResponse response = caseService.createCase(authorId,
                new CreateCaseRequest(CaseType.classic, "Titulo de prueba largo",
                        "Contenido del lado A suficientemente largo", "Relationship",
                        null, null, null, null, null));

        assertThat(response.status()).isEqualTo("PUBLIC");
        assertThat(response.type()).isEqualTo("classic");
        assertThat(response.side_a_user().username()).startsWith("user_");
        var saved = captureSaved();
        assertThat(saved.getInviteToken()).isNull();
    }

    @Test
    void createVoteCaseIsWaitingWithInviteToken() {
        when(caseRepository.save(any())).thenAnswer(inv -> withUuid(inv.getArgument(0)));

        CaseResponse response = caseService.createCase(authorId,
                new CreateCaseRequest(CaseType.vote, "Titulo de prueba largo",
                        "Contenido del lado A suficientemente largo", null,
                        null, sideBId.toString(), "A", "B", "Ambos"));

        assertThat(response.status()).isEqualTo("WAITING");
        var saved = captureSaved();
        assertThat(saved.getInviteToken()).isNotBlank();
        assertThat(saved.getSideBUserId()).isEqualTo(sideBId);
    }

    @Test
    void classicCaseRejectsSideB() {
        assertThatThrownBy(() -> caseService.createCase(authorId,
                new CreateCaseRequest(CaseType.classic, "Titulo de prueba largo",
                        "Contenido del lado A suficientemente largo", null,
                        null, sideBId.toString(), null, null, null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Solo los casos de votacion");
        verify(caseRepository, never()).save(any());
    }

    @Test
    void rejectsSelfAsSideB() {
        assertThatThrownBy(() -> caseService.createCase(authorId,
                new CreateCaseRequest(CaseType.vote, "Titulo de prueba largo",
                        "Contenido del lado A suficientemente largo", null,
                        null, authorId.toString(), null, null, null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("No puedes asignarte a ti mismo");
    }

    @Test
    void deletedCaseIsNotFoundOnDetail() {
        CaseEntity deleted = minimalCase(false);
        deleted.setDeletedAt(java.time.Instant.now());
        when(caseRepository.findById(deleted.getId()))
                .thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> caseService.getCase(deleted.getId(), request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Caso no encontrado");
    }

    @Test
    void anonymousAuthorIsMaskedForOtherViewerButVisibleForSelf() {
        lenient().when(usersClient.summaries(anyList())).thenReturn(List.of(
                new UserSummary(authorId, "anon_user", "https://example.com/x.png", true)));
        CaseEntity entity = minimalCase(true);
        when(caseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        // Otro usuario ve identidad oculta
        lenient().when(currentUserResolver.currentUserId(request))
                .thenReturn(Optional.of(UUID.randomUUID()));
        CaseResponse masked = caseService.getCase(entity.getId(), request);
        assertThat(masked.side_a_user().username())
                .isEqualTo(CaseService.MASKED_USERNAME);
        assertThat(masked.side_a_user().avatar_url())
                .isEqualTo(CaseService.MASKED_AVATAR);

        // El autor se ve a sí mismo
        when(currentUserResolver.currentUserId(request))
                .thenReturn(Optional.of(authorId));
        CaseResponse self = caseService.getCase(entity.getId(), request);
        assertThat(self.side_a_user().username()).isEqualTo("anon_user");
    }

    @Test
    void respondAsSideBActivatesCaseAndClearsToken() {
        CaseEntity waiting = minimalCase(false);
        waiting.setType(CaseType.vote);
        waiting.setStatus(CaseStatus.WAITING);
        waiting.setInviteToken("tok-123");
        when(caseRepository.findByInviteTokenAndDeletedAtIsNull("tok-123"))
                .thenReturn(Optional.of(waiting));

        CaseResponse response = caseService.respondAsSideB(sideBId,
                new RespondSideBRequest("tok-123", "Mi version de los hechos es esta.", null));

        assertThat(response.status()).isEqualTo("PUBLIC");
        assertThat(response.side_b_content())
                .isEqualTo("Mi version de los hechos es esta.");
        assertThat(response.side_a_user()).isNotNull();
    }

    @Test
    void respondRejectsForeignUserWhenSideBReserved() {
        CaseEntity waiting = minimalCase(false);
        waiting.setType(CaseType.vote);
        waiting.setStatus(CaseStatus.WAITING);
        waiting.setInviteToken("tok-456");
        waiting.setSideBUserId(sideBId);
        when(caseRepository.findByInviteTokenAndDeletedAtIsNull("tok-456"))
                .thenReturn(Optional.of(waiting));

        UUID intruder = UUID.randomUUID();
        assertThatThrownBy(() -> caseService.respondAsSideB(intruder,
                new RespondSideBRequest("tok-456", "Contenido de respuesta valida.", null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("solo puede ser utilizado");
    }

    @Test
    void respondRejectsAuthorAndAlreadyPublic() {
        CaseEntity entity = minimalCase(false);
        entity.setType(CaseType.vote);
        entity.setStatus(CaseStatus.WAITING);
        entity.setInviteToken("tok-789");
        when(caseRepository.findByInviteTokenAndDeletedAtIsNull("tok-789"))
                .thenReturn(Optional.of(entity));

        // autor no puede responder su propio caso
        assertThatThrownBy(() -> caseService.respondAsSideB(authorId,
                new RespondSideBRequest("tok-789", "Contenido de respuesta valida.", null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("No puedes responder tu propio caso");

        // caso PUBLIC ya no espera respuesta
        entity.setStatus(CaseStatus.PUBLIC);
        assertThatThrownBy(() -> caseService.respondAsSideB(sideBId,
                new RespondSideBRequest("tok-789", "Contenido de respuesta valida.", null)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("ya no está esperando");
    }

    @Test
    void inviteLinkReturnsExistingTokenForWaitingVoteCase() {
        CaseEntity waiting = minimalCase(false);
        waiting.setType(CaseType.vote);
        waiting.setStatus(CaseStatus.WAITING);
        waiting.setInviteToken("tok-exist");
        when(caseRepository.findById(waiting.getId())).thenReturn(Optional.of(waiting));

        var link = caseService.getOrRegenerateInviteLink(authorId, waiting.getId());

        assertThat(link.invite_token()).isEqualTo("tok-exist");
        assertThat(link.invite_url()).isEqualTo("http://localhost:3000/case/tok-exist");
    }

    @Test
    void inviteLinkRejectsClassicOrNonOwner() {
        CaseEntity classic = minimalCase(false);
        when(caseRepository.findById(classic.getId())).thenReturn(Optional.of(classic));

        assertThatThrownBy(() -> caseService.getOrRegenerateInviteLink(authorId,
                classic.getId()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Solo los casos de votación");
    }

    private CaseEntity minimalCase(boolean anonymous) {
        CaseEntity entity = new CaseEntity();
        entity.setType(CaseType.classic);
        entity.setTitle("Caso de prueba");
        entity.setSideAContent("Contenido de prueba");
        entity.setStatus(CaseStatus.PUBLIC);
        entity.setAnonymous(anonymous);
        entity.setSideAUserId(authorId);
        try {
            var field = CaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }

    private CaseEntity captureSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseRepository).save(captor.capture());
        return captor.getValue();
    }

    private CaseEntity withUuid(CaseEntity entity) {
        try {
            var field = CaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entity;
    }
}