package com.etribunal.identity.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplates {

    private final String appUrl;

    public EmailTemplates(
            @Value("${etribunal.frontend.url:http://localhost:3000}") String appUrl) {
        this.appUrl = appUrl;
    }

    public String passwordResetBody(String resetToken, String language) {
        String resetUrl = appUrl + "/reset-password?token=" + resetToken;
        if ("en".equals(language)) {
            return htmlWrapper(
                    "Reset your password",
                    "You have requested to reset your password on eTribunal.",
                    "Click the button below to create a new password:",
                    resetUrl,
                    "Reset Password",
                    "This link expires in 1 hour. If you didn't request this, you can ignore this email.");
        }
        return htmlWrapper(
                "Recuperar tu contraseña",
                "Has solicitado recuperar tu contraseña en eTribunal.",
                "Haz clic en el siguiente botón para crear una nueva contraseña:",
                resetUrl,
                "Recuperar Contraseña",
                "Este enlace expira en 1 hora. Si no solicitaste este cambio, puedes ignorar este correo.");
    }

    public String verificationBody(String verificationUrl, String language) {
        if ("en".equals(language)) {
            return htmlWrapper(
                    "Verify your email",
                    "Thank you for registering at eTribunal. Please verify your email:",
                    "",
                    verificationUrl,
                    "Verify Email",
                    "This link expires in 24 hours. If you didn't register, you can ignore this email.");
        }
        return htmlWrapper(
                "Verifica tu correo",
                "Gracias por registrarte en eTribunal. Por favor, verifica tu correo electrónico:",
                "",
                verificationUrl,
                "Verificar correo",
                "Este enlace expira en 24 horas. Si no solicitaste este registro, puedes ignorar este correo.");
    }

    private String htmlWrapper(
            String title,
            String greeting,
            String bodyText,
            String buttonUrl,
            String buttonText,
            String footerNote) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                  <h2 style="color: #333;">%s</h2>
                  <p style="color: #666; font-size: 16px;">%s</p>
                  %s<p style="color: #666; font-size: 16px;">%s</p>
                  <a href="%s" style="display: inline-block; background: #7c3aed; color: white; padding: 12px 24px; text-decoration: none; border-radius: 8px; margin: 16px 0;">
                    %s
                  </a>
                  <p style="color: #999; font-size: 14px;">%s</p>
                  <hr style="border: none; border-top: 1px solid #eee; margin: 24px 0;">
                  <p style="color: #ccc; font-size: 12px;">eTribunal - Tu Tribunal Social</p>
                </div>
                """
                .formatted(title, greeting, bodyText.isEmpty() ? "" : "<p>" + bodyText + "</p>", "", buttonUrl, buttonText, footerNote);
    }
}
