package com.queuejob.notificationservice.service;

import com.queuejob.notificationservice.event.JobCompletedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends HTML email notifications using Spring Mail and Thymeleaf templates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.mail.from}")
    private String fromAddress;

    @Value("${notification.mail.to-default}")
    private String defaultRecipient;

    /**
     * Sends a job completion notification email using the Thymeleaf template.
     *
     * @param event the completed job event containing job details
     */
    public void sendJobCompletedEmail(JobCompletedEvent event) {
        try {
            // Build Thymeleaf context with job details
            Context context = new Context();
            context.setVariable("jobId", event.getJobId().toString());
            context.setVariable("jobType", event.getType());
            context.setVariable("priority", event.getPriority());
            context.setVariable("payload", event.getPayload());
            context.setVariable("retryCount", event.getRetryCount() != null ? event.getRetryCount() : 0);
            context.setVariable("createdAt", event.getCreatedAt());
            context.setVariable("completedAt", event.getCompletedAt());

            // Render the HTML template
            String htmlBody = templateEngine.process("job-completed", context);

            // Build and send the MIME message
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(defaultRecipient);
            helper.setSubject("✅ Job Completed — " + event.getType() + " [" + event.getJobId() + "]");
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);

            log.info("Sent job completion email for job {} to {}", event.getJobId(), defaultRecipient);

        } catch (MessagingException | MailException ex) {
            log.error("Failed to send job completion email for job {}: {}",
                    event.getJobId(), ex.getMessage(), ex);
        }
    }
}
