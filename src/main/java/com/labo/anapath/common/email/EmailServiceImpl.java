package com.labo.anapath.common.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final Environment environment;

    @Value("${app.mail.from:noreply@labo-anapath.bj}")
    private String fromEmail;

    @Value("${app.mail.from-name:Labo AnaPath}")
    private String fromName;

    @Async
    @Override
    public void sendOtp(String to, String firstname, String otp) {
        // En profil dev, on affiche l'OTP en clair dans la console pour permettre la
        // connexion même quand le SMTP n'est pas configuré. JAMAIS actif en prod.
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("dev"))) {
            log.warn("[DEV] Code OTP 2FA pour {} : {}", maskEmail(to), otp);
        }
        try {
            Context context = new Context();
            context.setVariable("firstname", firstname);
            context.setVariable("otp", otp);
            context.setVariable("expiryMinutes", 10);

            String htmlContent = templateEngine.process("email/otp-2fa", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("Votre code de connexion — Labo AnaPath");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email OTP envoyé à: {}", maskEmail(to));
        } catch (Exception e) {
            log.error("Échec d'envoi de l'email OTP à {}: {}", maskEmail(to), e.getMessage());
        }
    }

    @Async
    @Override
    public void sendMacroAlert(String to, String testOrderCode, String labName) {
        try {
            Context context = new Context();
            context.setVariable("testOrderCode", testOrderCode);
            context.setVariable("labName", labName);

            String htmlContent = templateEngine.process("email/macro-non-fait", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("Rappel de macro - Demande [" + testOrderCode + "]");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email alerte macro envoyé à {} pour la demande {}", maskEmail(to), testOrderCode);
        } catch (Exception e) {
            log.error("Échec d'envoi de l'alerte macro à {}: {}", maskEmail(to), e.getMessage());
        }
    }

    @Async
    @Override
    public void sendTimeoffRequestToAdmin(String to, String employeeName,
                                          String startDate, String endDate, String labName) {
        try {
            Context context = new Context();
            context.setVariable("employeeName", employeeName);
            context.setVariable("startDate", startDate);
            context.setVariable("endDate", endDate);
            context.setVariable("labName", labName);

            String htmlContent = templateEngine.process("email/timeoff-admin", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("Notification : Une demande de congé");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email demande de congé (admin) envoyé à {} pour {}", maskEmail(to), employeeName);
        } catch (Exception e) {
            log.error("Échec d'envoi de la notification congé (admin) à {}: {}", maskEmail(to), e.getMessage());
        }
    }

    @Async
    @Override
    public void sendTimeoffApprovedToEmployee(String to, String employeeName,
                                              String startDate, String endDate, String labName) {
        try {
            Context context = new Context();
            context.setVariable("employeeName", employeeName);
            context.setVariable("startDate", startDate);
            context.setVariable("endDate", endDate);
            context.setVariable("labName", labName);

            String htmlContent = templateEngine.process("email/timeoff-employee", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("Notification : Une demande de congé");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email congé traité (employé) envoyé à {}", maskEmail(to));
        } catch (Exception e) {
            log.error("Échec d'envoi de la notification congé (employé) à {}: {}", maskEmail(to), e.getMessage());
        }
    }

    @Async
    @Override
    public void sendAssignedReview(String to, String reviewerName, String reportTitle,
                                   String testOrderCode, String labName) {
        try {
            Context context = new Context();
            context.setVariable("reviewerName", reviewerName);
            context.setVariable("reportTitle", reportTitle);
            context.setVariable("testOrderCode", testOrderCode);
            context.setVariable("labName", labName);

            String htmlContent = templateEngine.process("email/assigned-review", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("Notification : Vous avez été ajouté comme réviseur de compte rendu");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email relecteur assigné envoyé à {} pour le compte-rendu {}", maskEmail(to), testOrderCode);
        } catch (Exception e) {
            log.error("Échec d'envoi de la notification relecteur à {}: {}", maskEmail(to), e.getMessage());
        }
    }

    @Async
    @Override
    public void sendNewTicketAlert(String to, String ticketCode, String createdByName, String labName) {
        try {
            Context context = new Context();
            context.setVariable("ticketCode", ticketCode);
            context.setVariable("createdByName", createdByName);
            context.setVariable("labName", labName);

            String htmlContent = templateEngine.process("email/new-ticket", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("Création d'un nouveau ticket");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email nouveau ticket envoyé à {} pour le ticket {}", maskEmail(to), ticketCode);
        } catch (Exception e) {
            log.error("Échec d'envoi de la notification ticket à {}: {}", maskEmail(to), e.getMessage());
        }
    }

    @Async
    @Override
    public void sendReportNonFaitAlert(String to, String doctorName, String testOrderCode,
                                       int days, String labName) {
        try {
            Context context = new Context();
            context.setVariable("doctorName", doctorName);
            context.setVariable("testOrderCode", testOrderCode);
            context.setVariable("days", days);
            context.setVariable("labName", labName);

            String htmlContent = templateEngine.process("email/report-non-fait", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("Rappel de compte rendu - Demande [" + testOrderCode + "]");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email rappel compte-rendu envoyé à {} pour la demande {}", maskEmail(to), testOrderCode);
        } catch (Exception e) {
            log.error("Échec d'envoi du rappel compte-rendu à {}: {}", maskEmail(to), e.getMessage());
        }
    }

    @Async
    @Override
    public void sendShareDoc(String to, String recipientName, String sharerName,
                             String docTitle, String labName) {
        try {
            Context context = new Context();
            context.setVariable("recipientName", recipientName);
            context.setVariable("sharerName", sharerName);
            context.setVariable("docTitle", docTitle);
            context.setVariable("labName", labName);

            String htmlContent = templateEngine.process("email/share-doc", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject("Notification : Un document vous a été partagé");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email partage document envoyé à {} pour « {} »", maskEmail(to), docTitle);
        } catch (Exception e) {
            log.error("Échec d'envoi de la notification de partage à {}: {}", maskEmail(to), e.getMessage());
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String user = parts[0];
        String masked = user.length() > 2
                ? user.charAt(0) + "***" + user.charAt(user.length() - 1)
                : "***";
        return masked + "@" + parts[1];
    }
}
