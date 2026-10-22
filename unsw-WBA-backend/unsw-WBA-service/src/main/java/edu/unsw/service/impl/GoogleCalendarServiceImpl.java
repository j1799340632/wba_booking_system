package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import edu.unsw.core.config.GoogleCalendarProperties;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.GoogleCalendarConnectionMapper;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.GoogleCalendarConnection;
import edu.unsw.service.GoogleCalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * @author Stanley
 * @date 26/7/2026
 * @description Google Calendar integration-related services implementation
 */
@Service
public class GoogleCalendarServiceImpl implements GoogleCalendarService {
    private final Logger logger = LoggerFactory.getLogger(GoogleCalendarServiceImpl.class);

    private static final String APPLICATION_NAME =
            "WBA Platform";

    private static final String CALENDAR_ID =
            "primary";

    private static final String TIME_ZONE =
            "Australia/Sydney";

    private final GoogleCalendarProperties
            googleCalendarProperties;

    private final GoogleCalendarConnectionMapper
            googleCalendarConnectionMapper;

    private final AssessmentBookingMapper
            assessmentBookingMapper;

    private final AssessmentSlotMapper
            assessmentSlotMapper;

    public GoogleCalendarServiceImpl(
            GoogleCalendarProperties googleCalendarProperties,
            GoogleCalendarConnectionMapper googleCalendarConnectionMapper,
            AssessmentBookingMapper assessmentBookingMapper,
            AssessmentSlotMapper assessmentSlotMapper
    ) {
        this.googleCalendarProperties =
                googleCalendarProperties;

        this.googleCalendarConnectionMapper =
                googleCalendarConnectionMapper;

        this.assessmentBookingMapper =
                assessmentBookingMapper;

        this.assessmentSlotMapper =
                assessmentSlotMapper;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        GoogleAuthorizationCodeRequestUrl authorizationUrl =
                new GoogleAuthorizationCodeRequestUrl(
                        googleCalendarProperties.getClientId(),
                        googleCalendarProperties.getRedirectUri(),
                        List.of(
                                CalendarScopes.CALENDAR_EVENTS
                        )
                );

        authorizationUrl.setAccessType("offline");
        authorizationUrl.setState(state);
        authorizationUrl.set("prompt", "consent");

        return authorizationUrl.build();
    }

    @Override
    public GoogleTokenResponse exchangeCodeForTokens(
            String code
    ) throws IOException {
        return new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                googleCalendarProperties.getClientId(),
                googleCalendarProperties.getClientSecret(),
                code,
                googleCalendarProperties.getRedirectUri()
        ).execute();
    }

    @Override
    public void saveConnection(
            Long userId,
            GoogleTokenResponse tokenResponse
    ) {
        GoogleCalendarConnection existingConnection =
                googleCalendarConnectionMapper.selectOne(
                        Wrappers
                                .<GoogleCalendarConnection>lambdaQuery()
                                .eq(
                                        GoogleCalendarConnection::getUserId,
                                        userId
                                )
                );

        LocalDateTime accessTokenExpiry =
                calculateAccessTokenExpiry(
                        tokenResponse.getExpiresInSeconds()
                );

        if (Objects.isNull(existingConnection)) {
            GoogleCalendarConnection connection =
                    new GoogleCalendarConnection();

            connection.setUserId(userId);
            connection.setAccessToken(
                    tokenResponse.getAccessToken()
            );
            connection.setRefreshToken(
                    tokenResponse.getRefreshToken()
            );
            connection.setAccessTokenExpiry(
                    accessTokenExpiry
            );
            connection.setIsEnable(true);
            connection.setCreator(userId);
            connection.setUpdater(userId);

            googleCalendarConnectionMapper.insert(
                    connection
            );

            return;
        }

        existingConnection.setAccessToken(
                tokenResponse.getAccessToken()
        );

        existingConnection.setAccessTokenExpiry(
                accessTokenExpiry
        );

        existingConnection.setIsEnable(true);
        existingConnection.setUpdater(userId);

        /*
         * Google may not return a new refresh token every
         * time. Keep the existing token when none is returned.
         */
        if (
                tokenResponse.getRefreshToken() != null
                        && !tokenResponse
                        .getRefreshToken()
                        .isBlank()
        ) {
            existingConnection.setRefreshToken(
                    tokenResponse.getRefreshToken()
            );
        }

        googleCalendarConnectionMapper.updateById(
                existingConnection
        );
    }

    @Override
    public void addBookingToCalendar(Long bookingId) {
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

        AssessmentBooking booking =
                assessmentBookingMapper.selectById(
                        bookingId
                );

        if (booking == null) {
            throw new BusinessException(
                    ResponseEnum.DATA_NOT_QUERIED.getCode(),
                    "Booking does not exist"
            );
        }

        AssessmentSlot slot =
                assessmentSlotMapper.selectById(
                        booking.getAssessmentSlotId()
                );

        if (slot == null) {
            throw new BusinessException(
                    ResponseEnum.DATA_NOT_QUERIED.getCode(),
                    "Assessment slot does not exist"
            );
        }

        Long currentUserId = currentUser.getId();

        /*
         * The student owns the booking through booking.creator,
         * while the supervisor owns the assessment slot through
         * slot.creator. Either participant may add the confirmed
         * booking to their own connected Google Calendar.
         */
        boolean isBookingStudent =
                Objects.equals(
                        booking.getCreator(),
                        currentUserId
                );

        boolean isSlotSupervisor =
                Objects.equals(
                        slot.getCreator(),
                        currentUserId
                );

        if (!isBookingStudent && !isSlotSupervisor) {
            throw new BusinessException(
                    ResponseEnum.NO_PERMISSION.getCode(),
                    "You do not have permission to add this booking "
                            + "to Google Calendar"
            );
        }

        GoogleCalendarConnection connection =
                getEnabledConnection(
                        currentUser.getId()
                );

        validateRefreshToken(connection);

        try {
            GoogleCredential credential =
                    buildCredential(connection);

            Calendar calendar =
                    buildCalendarClient(credential);

            Event event =
                    buildCalendarEvent(
                            booking,
                            slot
                    );

            Event createdEvent =
                    calendar.events()
                            .insert(CALENDAR_ID, event)
                            .execute();

            logger.info(
                    """
                            Google Calendar event created:
                            Event ID: {}
                            Event link: {}
                            Start: {}
                            End: {}
                            """
                    , createdEvent.getId(), createdEvent.getHtmlLink(), createdEvent.getStart(), createdEvent.getEnd());

            updateAccessToken(
                    connection,
                    credential
            );
        } catch (IOException _) {
            throw new BusinessException(
                    ResponseEnum.FAILURE.getCode(),
                    "Failed to add booking to Google Calendar"
            );
        }
    }

    private GoogleCalendarConnection getEnabledConnection(
            Long userId
    ) {
        GoogleCalendarConnection connection =
                googleCalendarConnectionMapper.selectOne(
                        Wrappers
                                .<GoogleCalendarConnection>lambdaQuery()
                                .eq(
                                        GoogleCalendarConnection::getUserId,
                                        userId
                                )
                                .eq(
                                        GoogleCalendarConnection::getIsEnable,
                                        true
                                )
                );

        if (connection == null) {
            throw new BusinessException(
                    ResponseEnum.DATA_NOT_QUERIED.getCode(),
                    "Google Calendar is not connected"
            );
        }

        return connection;
    }

    private void validateRefreshToken(
            GoogleCalendarConnection connection
    ) {
        if (
                connection.getRefreshToken() == null
                        || connection
                        .getRefreshToken()
                        .isBlank()
        ) {
            throw new BusinessException(
                    ResponseEnum.FAILURE.getCode(),
                    "Google Calendar refresh token is unavailable"
            );
        }
    }

    private GoogleCredential buildCredential(
            GoogleCalendarConnection connection
    ) throws IOException {
        GoogleCredential credential =
                new GoogleCredential.Builder()
                        .setTransport(
                                new NetHttpTransport()
                        )
                        .setJsonFactory(
                                GsonFactory.getDefaultInstance()
                        )
                        .setClientSecrets(
                                googleCalendarProperties
                                        .getClientId(),
                                googleCalendarProperties
                                        .getClientSecret()
                        )
                        .build();

        credential.setRefreshToken(
                connection.getRefreshToken()
        );

        boolean refreshed =
                credential.refreshToken();

        if (!refreshed) {
            throw new BusinessException(
                    ResponseEnum.FAILURE.getCode(),
                    "Failed to refresh Google access token"
            );
        }

        return credential;
    }

    private Calendar buildCalendarClient(
            GoogleCredential credential
    ) {
        return new Calendar.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        )
                .setApplicationName(
                        APPLICATION_NAME
                )
                .build();
    }

    private Event buildCalendarEvent(
            AssessmentBooking booking,
            AssessmentSlot slot
    ) {
        ZoneId zoneId =
                ZoneId.of(TIME_ZONE);

        DateTime startDateTime =
                new DateTime(
                        booking.getStartTime()
                                .atZone(zoneId)
                                .toInstant()
                                .toEpochMilli()
                );

        DateTime endDateTime =
                new DateTime(
                        booking.getEndTime()
                                .atZone(zoneId)
                                .toInstant()
                                .toEpochMilli()
                );

        String eventTitle =
                slot.getType()
                        + " WBA Assessment";

        String eventDescription =
                "WBA assessment booking\n\n"
                        + "Booking ID: "
                        + booking.getId()
                        + "\nHospital: "
                        + valueOrEmpty(
                        slot.getHospital()
                )
                        + "\nDepartment: "
                        + valueOrEmpty(
                        slot.getDepartment()
                );

        Event event =
                new Event()
                        .setSummary(eventTitle)
                        .setDescription(
                                eventDescription
                        )
                        .setLocation(
                                slot.getLocation()
                        );

        event.setStart(
                new EventDateTime()
                        .setDateTime(
                                startDateTime
                        )
                        .setTimeZone(
                                TIME_ZONE
                        )
        );

        event.setEnd(
                new EventDateTime()
                        .setDateTime(
                                endDateTime
                        )
                        .setTimeZone(
                                TIME_ZONE
                        )
        );

        return event;
    }

    private void updateAccessToken(
            GoogleCalendarConnection connection,
            GoogleCredential credential
    ) {
        connection.setAccessToken(
                credential.getAccessToken()
        );

        connection.setAccessTokenExpiry(
                calculateAccessTokenExpiry(
                        credential.getExpiresInSeconds()
                )
        );

        googleCalendarConnectionMapper.updateById(
                connection
        );
    }

    private LocalDateTime calculateAccessTokenExpiry(
            Long expiresInSeconds
    ) {
        if (expiresInSeconds == null) {
            return null;
        }

        return LocalDateTime.now(ZoneId.systemDefault())
                .plusSeconds(expiresInSeconds);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public boolean isCalendarConnected() {
        CurrentUser currentUser =
                CurrentUserUtils.getUser();

        if (
                currentUser == null
                        || currentUser.getId() == null
        ) {
            return false;
        }

        GoogleCalendarConnection connection =
                googleCalendarConnectionMapper.selectOne(
                        Wrappers
                                .<GoogleCalendarConnection>lambdaQuery()
                                .eq(
                                        GoogleCalendarConnection::getUserId,
                                        currentUser.getId()
                                )
                                .eq(
                                        GoogleCalendarConnection::getIsEnable,
                                        true
                                )
                                .isNotNull(
                                        GoogleCalendarConnection::getRefreshToken
                                )
                                .last("LIMIT 1")
                );

        return connection != null;
    }
}