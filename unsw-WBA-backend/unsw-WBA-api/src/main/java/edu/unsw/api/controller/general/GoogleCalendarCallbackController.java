package edu.unsw.api.controller.general;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.service.GoogleCalendarService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;


@RestController
@RequestMapping("/general/calendar/google")
public class GoogleCalendarCallbackController {

    private static final String OAUTH_STATE_KEY =
            "googleCalendarOAuthState";

    private static final String OAUTH_USER_ID_KEY =
            "googleCalendarUserId";

    private static final String OAUTH_RETURN_URL_KEY =
            "googleCalendarReturnUrl";

    private final GoogleCalendarService
            googleCalendarService;

    public GoogleCalendarCallbackController(
            GoogleCalendarService googleCalendarService
    ) {
        this.googleCalendarService =
                googleCalendarService;
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpSession session
    ) throws IOException {
        String savedState =
                (String) session.getAttribute(
                        OAUTH_STATE_KEY
                );

        if (
                savedState == null
                        || !savedState.equals(state)
        ) {
            throw new BusinessException(
                    ResponseEnum.FAILURE.getCode(),
                    "OAuth state invalid"
            );
        }

        Long userId =
                (Long) session.getAttribute(
                        OAUTH_USER_ID_KEY
                );

        if (userId == null) {
            throw new BusinessException(
                    ResponseEnum.NO_PERMISSION.getCode(),
                    "Google Calendar connection session expired"
            );
        }

        GoogleTokenResponse tokenResponse =
                googleCalendarService
                        .exchangeCodeForTokens(code);

        googleCalendarService.saveConnection(
                userId,
                tokenResponse
        );

        String returnUrl =
                (String) session.getAttribute(
                        OAUTH_RETURN_URL_KEY
                );

        if (
                returnUrl == null
                        || !returnUrl.startsWith("/")
                        || returnUrl.startsWith("//")
        ) {
            returnUrl = "/home";
        }

        session.removeAttribute(OAUTH_STATE_KEY);
        session.removeAttribute(OAUTH_USER_ID_KEY);
        session.removeAttribute(OAUTH_RETURN_URL_KEY);

        String separator = returnUrl.contains("?") ? "&" : "?";

        String frontendUrl = returnUrl + separator + "calendarConnected=true";

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl))
                .build();
    }
}