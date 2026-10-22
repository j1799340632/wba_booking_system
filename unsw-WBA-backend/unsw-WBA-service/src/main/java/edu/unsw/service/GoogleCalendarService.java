package edu.unsw.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;

import java.io.IOException;

/**
 * @author Stanley
 * @date 26/7/2026
 * @description Google Calendar integration-related services
 */
public interface GoogleCalendarService {

    /**
     * Build the Google OAuth authorization URL.
     *
     * @param state Random state value used to prevent CSRF attacks
     * @return Google OAuth authorization URL
     */
    String buildAuthorizationUrl(String state);

    /**
     * Exchange the authorization code for Google OAuth tokens.
     *
     * @param code Authorization code returned by Google
     * @return Google OAuth token response
     * @throws IOException If the token exchange fails
     */
    GoogleTokenResponse exchangeCodeForTokens(
            String code
    ) throws IOException;

    /**
     * Save or update the Google Calendar connection.
     *
     * @param userId WBA user ID
     * @param tokenResponse Google OAuth token response
     */
    void saveConnection(
            Long userId,
            GoogleTokenResponse tokenResponse
    );

    /**
     * Check whether the current user has connected
     * their Google Calendar.
     *
     * @return true if connected, otherwise false
     */
    boolean isCalendarConnected();

    /**
     * Validate the booking and add it to the current
     * user's connected Google Calendar.
     *
     * @param bookingId Booking ID
     */
    void addBookingToCalendar(Long bookingId);
}