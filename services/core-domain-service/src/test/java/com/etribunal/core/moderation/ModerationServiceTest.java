package com.etribunal.core.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.ModerationStatus;
import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.cases.repository.CaseImageRepository;
import com.etribunal.core.comments.CommentEntity;
import com.etribunal.core.comments.CommentRepository;
import com.etribunal.core.reports.ReportStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock
    private ModerationProvider provider;

    @Mock
    private ModerationQueue queue;

    @Mock
    private ModerationLogRepository logRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CaseImageRepository caseImageRepository;

    private ModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ModerationService(provider, queue, logRepository,
                caseRepository, commentRepository, caseImageRepository);
    }

    @Test
    void moderateCaseContentSyncCallsProviderAndPersists() {
        UUID caseId = UUID.randomUUID();
        ModerationResult result = new ModerationResult(ModerationStatus.APPROVED, 0.1, List.of(), Map.of());

        when(provider.moderateText(anyString())).thenReturn(Mono.just(result));
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(new CaseEntity()));

        ModerationResult returned = moderationService.moderateCaseContentSync(
                caseId, "title", "side A", "side B").block();

        assertThat(returned.status()).isEqualTo(ModerationStatus.APPROVED);

        verify(provider).moderateText(anyString());
        ArgumentCaptor<ModerationLogEntity> logCaptor = ArgumentCaptor.forClass(ModerationLogEntity.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getTargetType()).isEqualTo("CASE");
    }

    @Test
    void moderateCommentSyncCallsProviderAndPersists() {
        UUID commentId = UUID.randomUUID();
        ModerationResult result = new ModerationResult(ModerationStatus.FLAGGED, 0.8, List.of("spam"), Map.of());

        when(provider.moderateText(anyString())).thenReturn(Mono.just(result));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(new CommentEntity()));

        ModerationResult returned = moderationService.moderateCommentSync(commentId, "contenido").block();

        assertThat(returned.status()).isEqualTo(ModerationStatus.FLAGGED);
        verify(provider).moderateText("contenido");
    }

    @Test
    void moderateCaseImageSyncCallsProviderAndPersists() {
        UUID imageId = UUID.randomUUID();
        ModerationResult result = new ModerationResult(ModerationStatus.REJECTED, 0.95, List.of("nsfw"), Map.of());

        when(provider.moderateImage(anyString())).thenReturn(Mono.just(result));
        when(caseImageRepository.findById(imageId)).thenReturn(Optional.of(new CaseImageEntity()));

        ModerationResult returned = moderationService.moderateCaseImageSync(imageId, "https://img/1.jpg").block();

        assertThat(returned.status()).isEqualTo(ModerationStatus.REJECTED);
        verify(provider).moderateImage("https://img/1.jpg");
    }

    @Test
    void moderateCaseContentAsyncEnqueuesJob() {
        UUID caseId = UUID.randomUUID();

        moderationService.moderateCaseContentAsync(caseId, "t", "a", "b");

        verify(queue).enqueue(any());
    }

    @Test
    void moderateCommentAsyncEnqueuesJob() {
        UUID commentId = UUID.randomUUID();

        moderationService.moderateCommentAsync(commentId, "comentario");

        verify(queue).enqueue(any());
    }

    @Test
    void moderateCaseImageAsyncEnqueuesJob() {
        UUID imageId = UUID.randomUUID();

        moderationService.moderateCaseImageAsync(imageId, "https://img/1.jpg");

        verify(queue).enqueue(any());
    }

    @Test
    void queueSizeDelegatesToQueue() {
        when(queue.size()).thenReturn(5);

        assertThat(moderationService.queueSize()).isEqualTo(5);
        verify(queue).size();
    }

    @Test
    void processQueuedJobsProcessesCaseJob() {
        UUID caseId = UUID.randomUUID();
        ModerationQueue.ModerationJob job = new ModerationQueue.ModerationJob("CASE", caseId, "contenido");
        lenient().when(queue.poll()).thenReturn(job, null);
        lenient().when(caseRepository.findById(caseId)).thenReturn(Optional.of(new CaseEntity()));
        lenient().when(provider.moderateText(anyString())).thenReturn(Mono.just(new ModerationResult(ModerationStatus.APPROVED, 0.1, List.of(), Map.of())));

        moderationService.processQueuedJobs();

        verify(queue, org.mockito.Mockito.atLeastOnce()).poll();
        verify(caseRepository, org.mockito.Mockito.atLeastOnce()).findById(caseId);
        verify(provider).moderateText("contenido");
    }

    @Test
    void processQueuedJobsProcessesCommentJob() {
        UUID commentId = UUID.randomUUID();
        ModerationQueue.ModerationJob job = new ModerationQueue.ModerationJob("COMMENT", commentId, "contenido");
        lenient().when(queue.poll()).thenReturn(job, null);
        lenient().when(commentRepository.findById(commentId)).thenReturn(Optional.of(new CommentEntity()));
        lenient().when(provider.moderateText(anyString())).thenReturn(Mono.just(new ModerationResult(ModerationStatus.APPROVED, 0.1, List.of(), Map.of())));

        moderationService.processQueuedJobs();

        verify(queue, org.mockito.Mockito.atLeastOnce()).poll();
        verify(commentRepository, org.mockito.Mockito.atLeastOnce()).findById(commentId);
    }

    @Test
    void processQueuedJobsProcessesCaseImageJob() {
        UUID imageId = UUID.randomUUID();
        ModerationQueue.ModerationJob job = new ModerationQueue.ModerationJob("CASE_IMAGE", imageId, "https://img/1.jpg");
        lenient().when(queue.poll()).thenReturn(job, null);
        lenient().when(caseImageRepository.findById(imageId)).thenReturn(Optional.of(new CaseImageEntity()));
        lenient().when(provider.moderateImage(anyString())).thenReturn(Mono.just(new ModerationResult(ModerationStatus.APPROVED, 0.1, List.of(), Map.of())));

        moderationService.processQueuedJobs();

        verify(queue, org.mockito.Mockito.atLeastOnce()).poll();
        verify(caseImageRepository, org.mockito.Mockito.atLeastOnce()).findById(imageId);
    }

    @Test
    void processQueuedJobsSkipsMissingEntities() {
        UUID caseId = UUID.randomUUID();
        ModerationQueue.ModerationJob job = new ModerationQueue.ModerationJob("CASE", caseId, "contenido");
        lenient().when(queue.poll()).thenReturn(job, null);
        lenient().when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        moderationService.processQueuedJobs();

        verify(queue, org.mockito.Mockito.atLeastOnce()).poll();
        verify(caseRepository, org.mockito.Mockito.atLeastOnce()).findById(caseId);
        verify(provider, org.mockito.Mockito.never()).moderateText(anyString());
    }

    @Test
    void getModerationHistoryDelegatesToRepository() {
        UUID targetId = UUID.randomUUID();
        List<ModerationLogEntity> history = List.of(new ModerationLogEntity());
        when(logRepository.findByTargetTypeAndTargetId("CASE", targetId)).thenReturn(history);

        assertThat(moderationService.getModerationHistory("CASE", targetId)).hasSize(1);
    }

    @Test
    void getFlaggedContentDelegatesToRepository() {
        List<ModerationLogEntity> flagged = List.of(new ModerationLogEntity());
        when(logRepository.findByModerationStatus(ModerationStatus.FLAGGED)).thenReturn(flagged);

        assertThat(moderationService.getFlaggedContent()).hasSize(1);
    }
}