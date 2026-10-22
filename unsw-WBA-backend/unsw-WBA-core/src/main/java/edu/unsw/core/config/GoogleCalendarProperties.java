package edu.unsw.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author Stanley Huang
 * @date 26/7/2026
 * @description Google Calendar configuration properties
 */
@Component
@ConfigurationProperties(prefix = "google.calendar")
public class GoogleCalendarProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}