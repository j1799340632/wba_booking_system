package edu.unsw.api.controller.general;

import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.service.GoogleCalendarService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/general/calendar/google")
public class GoogleCalendarController {

    private static final String OAUTH_STATE_KEY =
            "googleCalendarOAuthState";

    private static final String OAUTH_USER_ID_KEY =
            "googleCalendarUserId";

    private static final String OAUTH_RETURN_URL_KEY =
            "googleCalendarReturnUrl";

    private final GoogleCalendarService
            googleCalendarService;

    public GoogleCalendarController(
            GoogleCalendarService googleCalendarService
    ) {
        this.googleCalendarService =
                googleCalendarService;
    }

    @GetMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(
            @RequestParam(required = false)
            String returnUrl,
            HttpSession session
    ) {
        CurrentUser currentUser =
                CurrentUserUtils.getUser();

        if (
                currentUser == null
                        || currentUser.getId() == null
        ) {
            throw new BusinessException(
                    ResponseEnum.NO_PERMISSION
            );
        }

        String state =
                UUID.randomUUID().toString();

        session.setAttribute(
                OAUTH_STATE_KEY,
                state
        );

        session.setAttribute(
                OAUTH_USER_ID_KEY,
                currentUser.getId()
        );

        session.setAttribute(
                OAUTH_RETURN_URL_KEY,
                validateReturnUrl(returnUrl)
        );

        String authorizationUrl =
                googleCalendarService
                        .buildAuthorizationUrl(state);

        return ResponseEntity.ok(
                Map.of(
                        "code", 2000,
                        "message", "Success",
                        "data", Map.of(
                                "authorizationUrl",
                                authorizationUrl
                        )
                )
        );
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>>
    getCalendarStatus() {
        boolean connected =
                googleCalendarService
                        .isCalendarConnected();

        return ResponseEntity.ok(
                Map.of(
                        "code", 2000,
                        "message", "Success",
                        "data", Map.of(
                                "connected",
                                connected
                        )
                )
        );
    }

    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<Map<String, Object>>
    addBookingToCalendar(
            @PathVariable Long bookingId
    ) {
        googleCalendarService
                .addBookingToCalendar(bookingId);

        return ResponseEntity.ok(
                Map.of(
                        "code", 2000,
                        "message",
                        "Booking added to Google Calendar successfully",
                        "data", Map.of()
                )
        );
    }

    private String validateReturnUrl(
            String returnUrl
    ) {
        if (
                returnUrl != null
                        && returnUrl.startsWith("/")
                        && !returnUrl.startsWith("//")
        ) {
            return returnUrl;
        }

        return "redirect:/";
    }
}