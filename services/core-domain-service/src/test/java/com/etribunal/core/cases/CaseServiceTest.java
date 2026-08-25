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
import com.etribunal.core.security.CurrentUserResolver;
import com.etribunal.core.users.InternalUsersClient;
import com.etribunal.core.users.UserSummary;
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

    private CaseService caseService;

    private final UUID authorId = UUID.randomUUID();
    private final UUID sideBId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        caseService = new CaseService(caseRepository, usersClient, currentUserResolver);
        lenient().when(currentUserResolver.currentUserId(request))
                .thenReturn(Optional.of(authorId));
        lenient().when(usersClient.summaries(anyList())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> new UserSummary(id, "user_" + id.toString().substring(0, 4),
                            "https://example.com/a.png", false))
                    .toList();
        });
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
