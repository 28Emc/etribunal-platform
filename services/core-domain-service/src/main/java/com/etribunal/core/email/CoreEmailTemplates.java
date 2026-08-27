package com.etribunal.core.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CoreEmailTemplates {

    private final String appUrl;
    private final String moderatorEmail;

    public CoreEmailTemplates(
            @Value("${etribunal.frontend.url:http://localhost:3000}") String appUrl,
            @Value("${etribunal.email.moderator-to:moderator@etribunal.com}") String moderatorEmail) {
        this.appUrl = appUrl;
        this.moderatorEmail = moderatorEmail;
    }

    public String caseReportedBody(String caseTitle, String reportReason) {
        return htmlWrapper(
                "Tu caso ha sido reportado",
                "Tu caso <strong>" + escapeHtml(caseTitle) + "</strong> ha sido reportado por un moderador.",
                "Motivo: " + escapeHtml(reportReason),
                "",
                appUrl + "/cases/" + caseTitle,
                "Ver Caso");
    }

    public String caseReportedToModeratorBody(String caseTitle, String sideAContent, String caseType, String category, String creatorUsername, String caseId, java.util.List<String> imageUrls) {
        String imagesHtml = imageUrls != null && !imageUrls.isEmpty()
                ? imageUrls.stream().map(url -> "<li><a href=\"" + escapeHtml(url) + "\" target=\"_blank\">" + escapeHtml(url) + "</a></li>").reduce("", String::concat)
                : "<p>Sin imágenes adjuntas</p>";

        return htmlWrapper(
                "Nuevo caso reportado: " + escapeHtml(caseTitle),
                "Un nuevo caso con reporte ha sido creado en eTribunal.",
                "",
                "<table style=\"width: 100%; border-collapse: collapse; margin: 16px 0;\">" +
                        "<tr><td style=\"padding: 8px; border-bottom: 1px solid #eee; color: #999;\">Título</td><td style=\"padding: 8px; border-bottom: 1px solid #eee; color: #333;\"><strong>" + escapeHtml(caseTitle) + "</strong></td></tr>" +
                        "<tr><td style=\"padding: 8px; border-bottom: 1px solid #eee; color: #999;\">Tipo</td><td style=\"padding: 8px; border-bottom: 1px solid #eee; color: #333;\">" + escapeHtml(caseType) + "</td></tr>" +
                        "<tr><td style=\"padding: 8px; border-bottom: 1px solid #eee; color: #999;\">Categoría</td><td style=\"padding: 8px; border-bottom: 1px solid #eee; color: #333;\">" + escapeHtml(category) + "</td></tr>" +
                        "<tr><td style=\"padding: 8px; border-bottom: 1px solid #eee; color: #999;\">Creado por</td><td style=\"padding: 8px; border-bottom: 1px solid #eee; color: #333;\">" + escapeHtml(creatorUsername) + "</td></tr>" +
                        "</table>" +
                        "<p style=\"color: #666; font-size: 16px;\"><strong>Contenido:</strong></p>" +
                        "<p style=\"color: #666; font-size: 16px; background: #f5f5f5; padding: 12px; border-radius: 8px;\">" + escapeHtml(sideAContent) + "</p>" +
                        "<p style=\"color: #666; font-size: 16px;\"><strong>Imágenes adjuntas (" + (imageUrls != null ? imageUrls.size() : 0) + "):</strong></p>" +
                        "<ul style=\"color: #666; font-size: 14px;\">" + imagesHtml + "</ul>",
                appUrl + "/cases/" + caseId,
                "Ver Caso");
    }

    public String caseEditedAfterReportBody(String caseTitle, String caseId) {
        return htmlWrapper(
                "Caso reportado ha sido editado",
                "El caso <strong>" + escapeHtml(caseTitle) + "</strong> que reportaste ha sido editado por su creador.",
                "Por favor revisa los cambios y toma las acciones necesarias.",
                "",
                appUrl + "/cases/" + caseId,
                "Ver Caso");
    }

    private String htmlWrapper(String title, String greeting, String bodyText, String extraBody, String buttonUrl, String buttonText) {
        String wrapper = ""
                + "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">"
                + "  <h2 style=\"color: #333;\">" + title + "</h2>"
                + "  <p style=\"color: #666; font-size: 16px;\">" + greeting + "</p>"
                + (bodyText.isEmpty() ? "" : "  <p style=\"color: #666; font-size: 16px;\">" + bodyText + "</p>")
                + (extraBody.isEmpty() ? "" : extraBody)
                + "  <a href=\"" + buttonUrl + "\" style=\"display: inline-block; background: #7c3aed; color: white; padding: 12px 24px; text-decoration: none; border-radius: 8px; margin: 16px 0;\">"
                + buttonText + "</a>"
                + "  <hr style=\"border: none; border-top: 1px solid #eee; margin: 24px 0;\">"
                + "  <p style=\"color: #ccc; font-size: 12px;\">eTribunal - Tu Tribunal Social</p>"
                + "</div>";
        return wrapper;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&")
                .replace("<", "<")
                .replace(">", ">")
                .replace("\"", "\"")
                .replace("'", "'");
    }

    public String getModeratorEmail() {
        return moderatorEmail;
    }
}