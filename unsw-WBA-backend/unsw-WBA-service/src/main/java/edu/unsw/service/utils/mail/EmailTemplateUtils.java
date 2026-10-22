package edu.unsw.service.utils.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 20/7/2026 18:09
 * @description
 */
public class EmailTemplateUtils {
    private EmailTemplateUtils() {

    }

    private static final Logger logger = LoggerFactory.getLogger(EmailTemplateUtils.class);

    public static String buildCardHtml(String title, String username, String content) {
        return buildCardHtml(title, username, content, null);
    }

    public static String buildCardHtml(String title, String username, String content, String personal) {
        // No '/' before static
        String templatePath = "templates/email-card.html";
        ClassPathResource resource = new ClassPathResource(templatePath);

        // Must use getInputStream()
        try (InputStream inputStream = resource.getInputStream()) {

            String template = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            String sendDate = LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            return template
                    .replace("${title}", title != null ? title : "")
                    .replace("${username}", username != null ? username : "")
                    .replace("${content}", content != null ? content : "")
                    .replace("${personal}", personal != null ? personal : EmailSenderUtils.PERSONAL)
                    .replace("${sendDate}", sendDate);
        } catch (IOException e) {
            logger.error("Failed to read email template from {}, return plain text content", templatePath, e);
            return content;
        }
    }
}
