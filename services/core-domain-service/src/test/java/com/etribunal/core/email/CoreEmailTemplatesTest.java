package com.etribunal.core.email;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoreEmailTemplatesTest {

    private final CoreEmailTemplates templates = new CoreEmailTemplates(
            "https://app.etribunal.com", "/cases/", "mod@etribunal.com");

    @Test
    void caseReportedBodyContainsEscapedTitleAndReason() {
        String body = templates.caseReportedBody("Rompieron <contrato>", "Provoca & ofende");

        assertThat(body).contains("Tu caso ha sido reportado")
                .contains("Rompieron &lt;contrato&gt;")
                .contains("Provoca &amp; ofende")
                .contains("https://app.etribunal.com/cases/Rompieron <contrato>")
                .contains("Ver Caso");
    }

    @Test
    void caseReportedBodyEscapesQuotesAndAppends() {
        String body = templates.caseReportedBody("Caso \"especial\" 'final'", "Motivo");

        assertThat(body).contains("Caso &quot;especial&quot; &#39;final&#39;");
    }

    @Test
    void caseReportedToModeratorBodyWithImagesListsUrls() {
        String body = templates.caseReportedToModeratorBody(
                "Título", "Contenido", "VOTE", "legal", "user_1234", "abc-123", List.of("https://img/1.png"));

        assertThat(body).contains("Nuevo caso reportado: Título")
                .contains("VOTE")
                .contains("legal")
                .contains("user_1234")
                .contains("Contenido")
                .contains("Imágenes adjuntas (1)")
                .contains("https://img/1.png");
    }

    @Test
    void caseReportedToModeratorBodyWithoutImagesShowsNoImages() {
        String body = templates.caseReportedToModeratorBody(
                "Título", "Contenido", "CLASSIC", "general", "user_1234", "abc-123", List.of());

        assertThat(body).contains("Imágenes adjuntas (0)")
                .contains("Sin imágenes adjuntas");
    }

    @Test
    void caseReportedToModeratorBodyWithNullImagesShowsNoImages() {
        String body = templates.caseReportedToModeratorBody(
                "Título", "Contenido", "CLASSIC", "general", "user_1234", "abc-123", null);

        assertThat(body).contains("Imágenes adjuntas (0)")
                .contains("Sin imágenes adjuntas");
    }

    @Test
    void caseEditedAfterReportBodyContainsCaseLink() {
        String body = templates.caseEditedAfterReportBody("Caso editado", "case-42");

        assertThat(body).contains("Caso reportado ha sido editado")
                .contains("Caso editado")
                .contains("https://app.etribunal.com/cases/case-42")
                .contains("Ver Caso");
    }

    @Test
    void escapeHtmlHandlesNull() {
        assertThat(templates.caseReportedBody(null, "r"))
                .contains("Ver Caso");
    }

    @Test
    void getModeratorEmailReturnsConfiguredValue() {
        assertThat(templates.getModeratorEmail()).isEqualTo("mod@etribunal.com");
    }
}