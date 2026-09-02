package com.etribunal.core.translation.application;

import com.etribunal.core.cases.CaseEntity;
import com.etribunal.core.cases.CaseRepository;
import com.etribunal.core.comments.CommentEntity;
import com.etribunal.core.comments.CommentRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Traducción de casos y comentarios.
 *
 * Provider por defecto: "console-log" (mock, parity legacy). Devuelve el texto
 * original con prefijo [es→{lang}] para que en desarrollo sea evidente que no
 * hay traducción real. Un provider real (Google Cloud Translation / Gemini)
 * debe reemplazar este método.
 */
@Service
public class TranslationService {

    private static final String DEFAULT_SOURCE_LANGUAGE = "es";

    private final CaseRepository caseRepository;
    private final CommentRepository commentRepository;

    public TranslationService(CaseRepository caseRepository,
                              CommentRepository commentRepository) {
        this.caseRepository = caseRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional(readOnly = true)
    public TranslatedCaseResponse translateCase(UUID caseId, String targetLanguage) {
        CaseEntity c = caseRepository.findById(caseId)
                .filter(ca -> ca.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caso no encontrado"));

        String lang = normalize(targetLanguage);
        return new TranslatedCaseResponse(
                c.getId(),
                DEFAULT_SOURCE_LANGUAGE,
                lang,
                mock(c.getTitle(), lang),
                mock(c.getSideAContent(), lang),
                c.getSideBContent() != null ? mock(c.getSideBContent(), lang) : null,
                c.getSideASubtitle() != null ? mock(c.getSideASubtitle(), lang) : null,
                c.getSideBSubtitle() != null ? mock(c.getSideBSubtitle(), lang) : null,
                c.getBothWrongSubtitle() != null ? mock(c.getBothWrongSubtitle(), lang) : null);
    }

    @Transactional(readOnly = true)
    public TranslatedCommentResponse translateComment(UUID commentId, String targetLanguage) {
        CommentEntity comment = commentRepository.findById(commentId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Comentario no encontrado"));

        String lang = normalize(targetLanguage);
        return new TranslatedCommentResponse(
                comment.getId(),
                comment.getId(),
                DEFAULT_SOURCE_LANGUAGE,
                lang,
                mock(comment.getContent(), lang));
    }

    private static String normalize(String targetLanguage) {
        if (targetLanguage == null || targetLanguage.isBlank()) {
            return "en";
        }
        return targetLanguage.trim().toLowerCase();
    }

    private static String mock(String text, String targetLanguage) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return "[" + DEFAULT_SOURCE_LANGUAGE + "\u2192" + targetLanguage + "] " + text;
    }

    public record TranslatedCaseResponse(UUID id, String sourceLanguage, String targetLanguage,
                                         String title, String sideA, String sideB,
                                         String sideASubtitle, String sideBSubtitle,
                                         String bothWrongSubtitle) {
    }

    public record TranslatedCommentResponse(UUID id, UUID commentId, String sourceLanguage,
                                            String targetLanguage, String content) {
    }
}