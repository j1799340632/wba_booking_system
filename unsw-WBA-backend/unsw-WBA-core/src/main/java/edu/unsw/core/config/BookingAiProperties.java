package edu.unsw.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wba.booking.ai")
public class BookingAiProperties {
    private boolean enabled;
    private String endpoint = "https://api.deepseek.com/chat/completions";
    private String model = "deepseek-flash";
    private String apiKey = "";
    private int timeoutSeconds = 20;
    private boolean disableThinking = true;
}
