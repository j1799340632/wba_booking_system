package edu.unsw.service.utils.mail;

import edu.unsw.core.config.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 20/7/2026 16:35
 * @description Email sender utils
 */
@Service
@Async
public class EmailSenderUtils {
    private final Logger logger = LoggerFactory.getLogger(EmailSenderUtils.class);

    private final JavaMailSender mailSender;

    /**
     * If there is a red wavy line under mailSender, it's because the email configuration wasn't written to the application.properties configuration file
     */
    public EmailSenderUtils(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    static final String PERSONAL = "UNSW-WBA";

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Send text email
     *
     * @param to Receiver email address
     * @param subject Email subject
     * @param textContent Email content
     */
    public void sendSimpleMail(String to, String subject, String textContent) {
        sendSimpleMail(to, subject, textContent, PERSONAL);
    }

    /**
     * Send text email
     *
     * @param to Receiver email address
     * @param subject Email subject
     * @param textContent Email content
     * @param personal Sender alias
     */
    public void sendSimpleMail(String to, String subject, String textContent, String personal) {
        sendMail(
                new MailRequest()
                        .to(to)
                        .subject(subject)
                        .textBody(textContent)
                        .personal(personal)
        );
    }

    /**
     * Send HTML email by default template
     *
     * @param to Receiver email address
     * @param subject Email subject
     * @param receiverName Email receiver name
     * @param content Email content
     */
    public void sendHtmlMailByDefaultTemplate(String to, String subject, String receiverName, String content) {
        String cardHtml = EmailTemplateUtils.buildCardHtml(subject, receiverName, content);
        sendHtmlMail(to, subject, cardHtml, PERSONAL);
    }

    /**
     * Send HTML email
     *
     * @param to Receiver email address
     * @param subject Email subject
     * @param htmlContent Email content
     */
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        sendHtmlMail(to, subject, htmlContent, PERSONAL);
    }

    /**
     * Send HTML email
     *
     * @param to Receiver email address
     * @param subject Email subject
     * @param htmlContent Email content
     * @param personal Sender alias
     */
    public void sendHtmlMail(String to, String subject, String htmlContent, String personal) {
        sendMail(
                new MailRequest()
                        .to(to)
                        .subject(subject)
                        .htmlBody(htmlContent)
                        .personal(personal)
        );
    }


    /**
     * Handle all types of email requests
     *
     * @param request Unified email parameter request builder
     */
    public void sendMail(MailRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            // MULTIPART_MODE_MIXED_RELATED is enabled only if there are attachments or embedded images. If it is just plain HTML or plain text, use the normal mode to prevent the generation of redundant alternative structures
            MimeMessageHelper helper = getMimeMessageHelper(request, message);

            // Configure the sender with a custom alias (preferably using the personal alias specified in the request; otherwise, use the default value as a fallback)
            String personal = request.getPersonal() != null ? request.getPersonal() : PERSONAL;
            InternetAddress fromAddress = new InternetAddress(fromEmail, personal, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);

            // Set receiver email address and subject
            helper.setTo(request.getTo());
            helper.setSubject(request.getSubject());

            // Automatic text type detection: HTML is rendered first, otherwise plain text is rendered
            if (request.getHtmlBody() != null) {
                helper.setText(request.getHtmlBody(), true);
            } else if (request.getTextBody() != null) {
                helper.setText(request.getTextBody(), false);
            } else {
                throw new BusinessException("Mail content cannot be empty! Either textBody or htmlBody must be set.");
            }

            // Embed CID image resources (referenced within HTML using `<img src='cid:contentId'/>`)
            if (!request.getInlineImages().isEmpty()) {
                for (Map.Entry<String, File> entry : request.getInlineImages().entrySet()) {
                    helper.addInline(entry.getKey(), entry.getValue());
                }
            }

            // Document Attachment
            if (!request.getAttachments().isEmpty()) {
                for (Map.Entry<String, File> entry : request.getAttachments().entrySet()) {
                    helper.addAttachment(entry.getKey(), entry.getValue());
                }
            }

            // Byte stream attachments (such as Excel/PDF generated in memory)
            if (!request.getStreamAttachments().isEmpty()) {
                for (Map.Entry<String, InputStreamSource> entry : request.getStreamAttachments().entrySet()) {
                    helper.addAttachment(entry.getKey(), entry.getValue());
                }
            }

            mailSender.send(message);
            logger.info("Email sent successfully to [{}] with personal [{}]", request.getTo(), personal);
        } catch (Exception e) {
            logger.error("Failed to send email to [{}]", request.getTo(), e);
        }
    }

    private static MimeMessageHelper getMimeMessageHelper(MailRequest request, MimeMessage message) throws MessagingException {
        // Enable multipart mode (supports complex HTML, embedded resources, and attachments)
        boolean isMultipart = !request.getInlineImages().isEmpty()
                || !request.getAttachments().isEmpty()
                || !request.getStreamAttachments().isEmpty();

        return new MimeMessageHelper(
                message,
                isMultipart ? MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED : MimeMessageHelper.MULTIPART_MODE_NO,
                StandardCharsets.UTF_8.name()
        );
    }

    @Getter
    public static class MailRequest {
        // Getters
        private String to;
        private String subject;
        private String personal;
        private String textBody;
        private String htmlBody;

        // Storing CID image resources
        private final Map<String, File> inlineImages = new HashMap<>();

        // Attachment file
        private final Map<String, File> attachments = new HashMap<>();

        // File stream
        private final Map<String, InputStreamSource> streamAttachments = new HashMap<>();

        public MailRequest to(String to) {
            this.to = to;
            return this;
        }

        public MailRequest subject(String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * Customize the sender alias displayed in the current email (e.g., "XX System Notification")
         */
        public MailRequest personal(String personal) {
            this.personal = personal;
            return this;
        }

        /**
         * Set plain text content
         */
        public MailRequest textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        /**
         * Set HTML format content
         */
        public MailRequest htmlBody(String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        /**
         * Related HTML embedded images
         * @param contentId The name corresponding to `<img src='cid:contentId'/>` in HTML
         */
        public MailRequest addInlineImage(String contentId, File imageFile) {
            this.inlineImages.put(contentId, imageFile);
            return this;
        }

        /**
         * Document Attachment
         */
        public MailRequest addAttachment(String attachmentName, File file) {
            this.attachments.put(attachmentName, file);
            return this;
        }

        /**
         * Byte stream attachments (such as Excel/PDF generated in memory)
         */
        public MailRequest addAttachment(String attachmentName, InputStreamSource inputStreamSource) {
            this.streamAttachments.put(attachmentName, inputStreamSource);
            return this;
        }
    }
}
