package com.etribunal.core.email;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.cases.repository.CaseImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoreEmailServiceTest {

    @Mock
    private EmailProvider emailProvider;
    @Mock
    private CoreEmailTemplates templates;
    @Mock
    private CaseRepository caseRepository;
    @Mock
    private CaseImageRepository caseImageRepository;

    private CoreEmailService service;

    private UUID caseId;

    @BeforeEach
    void setUp() {
        service = new CoreEmailService(emailProvider, templates, caseRepository, caseImageRepository);
        caseId = UUID.randomUUID();
    }

    @Test
    void sendCaseReportedEmailSendsToCreatorWhenCaseExists() {
        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setTitle("Caso reportado");
        UUID creatorId = UUID.randomUUID();
        caseEntity.setSideAUserId(creatorId);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
        when(templates.caseReportedBody("Caso reportado", "spam")).thenReturn("<html>body</html>");

        service.sendCaseReportedEmail(caseId, "spam");

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailProvider).sendEmail(to.capture(), eq("Tu caso ha sido reportado - eTribunal"), body.capture());
        assertThat(to.getValue()).isEqualTo("user_" + creatorId.toString().substring(0, 8) + "@etribunal.local");
        assertThat(body.getValue()).isEqualTo("<html>body</html>");
    }

    @Test
    void sendCaseReportedEmailSkipsWhenCaseMissing() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        service.sendCaseReportedEmail(caseId, "spam");

        verify(emailProvider, never()).sendEmail(any(), any(), any());
    }

    @Test
    void sendCaseCreatedWithImagesEmailSendsToModeratorWithImageUrls() {
        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setTitle("Caso con imágenes");
        caseEntity.setSideAContent("Contenido");
        caseEntity.setType(com.etribunal.core.cases.CaseType.vote);
        caseEntity.setCategory("legal");
        caseEntity.setSideAUserId(UUID.randomUUID());

        CaseImageEntity img = new CaseImageEntity();
        img.setUrl("https://cdn/img/1.jpg");

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
        when(caseImageRepository.findByCaseIdOrderByOrderIndexAsc(caseId)).thenReturn(List.of(img));
        when(templates.getModeratorEmail()).thenReturn("mod@etribunal.com");
        when(templates.caseReportedToModeratorBody(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>mod body</html>");

        service.sendCaseCreatedWithImagesEmail(caseId);

        ArgumentCaptor<List<String>> urls = ArgumentCaptor.forClass(List.class);
        verify(templates).caseReportedToModeratorBody(
                eq("Caso con imágenes"), eq("Contenido"), eq("vote"), eq("legal"),
                any(), eq(caseId.toString()), urls.capture());
        assertThat(urls.getValue()).containsExactly("https://cdn/img/1.jpg");
        verify(emailProvider).sendEmail(eq("mod@etribunal.com"), eq("Nuevo caso con imágenes: Caso con imágenes - eTribunal"), any());
    }

    @Test
    void sendCaseCreatedWithImagesEmailSkipsWhenCaseMissing() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        service.sendCaseCreatedWithImagesEmail(caseId);

        verify(emailProvider, never()).sendEmail(any(), any(), any());
    }

    @Test
    void sendCaseEditedAfterReportEmailSendsToModerator() {
        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setTitle("Caso editado");
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(caseEntity));
        when(templates.getModeratorEmail()).thenReturn("mod@etribunal.com");
        when(templates.caseEditedAfterReportBody("Caso editado", caseId.toString())).thenReturn("<html>body</html>");

        service.sendCaseEditedAfterReportEmail(caseId);

        verify(emailProvider).sendEmail("mod@etribunal.com", "Caso reportado ha sido editado - eTribunal", "<html>body</html>");
    }

    @Test
    void sendCaseEditedAfterReportEmailSkipsWhenCaseMissing() {
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        service.sendCaseEditedAfterReportEmail(caseId);

        verify(emailProvider, never()).sendEmail(any(), any(), any());
    }

    @Test
    void getModeratorEmailDelegatesToTemplates() {
        when(templates.getModeratorEmail()).thenReturn("mod@etribunal.com");

        assertThat(service.getModeratorEmail()).isEqualTo("mod@etribunal.com");
    }
}