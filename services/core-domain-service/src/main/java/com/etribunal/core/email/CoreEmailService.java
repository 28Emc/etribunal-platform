package com.etribunal.core.email;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.cases.domain.CaseImageEntity;
import com.etribunal.core.cases.repository.CaseImageRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CoreEmailService {

    private static final Logger log = LoggerFactory.getLogger(CoreEmailService.class);

    private final EmailProvider emailProvider;
    private final CoreEmailTemplates templates;
    private final CaseRepository caseRepository;
    private final CaseImageRepository caseImageRepository;

    public CoreEmailService(
            EmailProvider emailProvider,
            CoreEmailTemplates templates,
            CaseRepository caseRepository,
            CaseImageRepository caseImageRepository) {
        this.emailProvider = emailProvider;
        this.templates = templates;
        this.caseRepository = caseRepository;
        this.caseImageRepository = caseImageRepository;
    }

    /**
     * Envía email al creador cuando su caso es reportado
     */
    @Transactional
    public void sendCaseReportedEmail(UUID caseId, String reportReason) {
        CaseEntity caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null) {
            log.warn("No se encontró caso {} para enviar email de reporte", caseId);
            return;
        }

        String body = templates.caseReportedBody(caseEntity.getTitle(), reportReason);
        emailProvider.sendEmail(
                resolveCreatorEmail(caseEntity),
                "Tu caso ha sido reportado - eTribunal",
                body);
        log.info("Email de caso reportado enviado para caso {}", caseId);
    }

    /**
     * Envía email al moderador cuando un caso nuevo tiene imágenes
     */
    @Transactional
    public void sendCaseCreatedWithImagesEmail(UUID caseId) {
        CaseEntity caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null) {
            log.warn("No se encontró caso {} para enviar email a moderador", caseId);
            return;
        }

        List<CaseImageEntity> images = caseImageRepository.findByCaseIdOrderByOrderIndexAsc(caseId);
        List<String> imageUrls = images.stream()
                .map(CaseImageEntity::getUrl)
                .toList();

        String body = templates.caseReportedToModeratorBody(
                caseEntity.getTitle(),
                caseEntity.getSideAContent(),
                caseEntity.getType().name(),
                caseEntity.getCategory(),
                resolveCreatorUsername(caseEntity),
                caseId.toString(),
                imageUrls);

        emailProvider.sendEmail(
                templates.getModeratorEmail(),
                "Nuevo caso con imágenes: " + caseEntity.getTitle() + " - eTribunal",
                body);
        log.info("Email a moderador enviado para caso {}", caseId);
    }

    /**
     * Envía email al moderador cuando un caso reportado es editado
     */
    @Transactional
    public void sendCaseEditedAfterReportEmail(UUID caseId) {
        CaseEntity caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null) {
            return;
        }

        String body = templates.caseEditedAfterReportBody(caseEntity.getTitle(), caseId.toString());
        emailProvider.sendEmail(
                templates.getModeratorEmail(),
                "Caso reportado ha sido editado - eTribunal",
                body);
        log.info("Email de caso editado enviado a moderador para caso {}", caseId);
    }

    public String getModeratorEmail() {
        return templates.getModeratorEmail();
    }

    private String resolveCreatorEmail(CaseEntity caseEntity) {
        if (caseEntity.getSideAUserId() == null) {
            return templates.getModeratorEmail();
        }
        return "user_" + caseEntity.getSideAUserId().toString().substring(0, 8) + "@etribunal.local";
    }

    private String resolveCreatorUsername(CaseEntity caseEntity) {
        if (caseEntity.getSideAUserId() == null) {
            return "unknown";
        }
        return "user_" + caseEntity.getSideAUserId().toString().substring(0, 4);
    }
}