package edu.unsw.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 18:29
 * @description Response code enumeration
 */
@Getter
@AllArgsConstructor
public enum ResponseEnum {

    /**
     * Response successful
     */
    SUCCESS(2000, "Response successful"),

    /**
     * Response failed
     */
    FAILURE(5000, "Unknown error"),

    NOT_LOGGED_IN(4001, "Not logged in"),
    NO_PERMISSION(4003, "No permission"),
    ACCOUNT_EXIST(5001, "Account already exists"),
    ACCOUNT_NOT_EXIST(5002, "Account not exists"),
    PASSWORD_ERROR(5003, "Password error"),
    PASSWORD_CONFLICT(5004, "New password cannot same as old password"),
    ACCOUNT_BLOCKED(5005, "Account blocked"),
    ACCOUNT_CANCELED(5006, "Account canceled"),
    USER_ROLE_NOT_FOUND(5007, "User role not found"),
    USER_ROLE_KEY_EXIST(5008, "User role key already exist"),
    USER_ROLE_IN_USE(5009, "User role in use, please del associate user first"),
    BASE_USER_ROLE_CANNOT_MODIFY(5010, "Base user role cannot be modified"),
    REQUEST_EXCEED_PERIOD(5011, "Request exceed period"),
    ILLEGAL_PARAM(5012, "Parameter validation failed"),
    LOCK_CONTROL(5013, "Locker control, try again"),
    VERIFICATION_EXPIRED(5014, "Verification expired"),
    VERIFICATION_ERROR(5015, "Verification code error"),
    DATA_NOT_QUERIED(5016, "No data found"),
    DATA_SAVE_FAILED(5017, "Save data failed"),
    BOOKING_CANCEL_TIME_LIMIT(5018, "Booking will begin in 48 hours, cannot cancel"),
    TIME_ILLEGAL(5019, "Please select the legal time"),
    EMAIL_SEND_FAILED(5020, "Email send failed"),
    BOOKING_CONFLICT(5021, "You still have unprocessed upcoming booking for this slot"),
    SLOT_CONFLICT(5022, "The period of this slot has been booked"),
    BOOKING_NOT_EXIST(5023, "The booking not exist"),
    SLOT_NOT_EXIST(5024, "The booking not exist"),

    FILE_NOT_EXIST(7000, "File not exist"),
    UPLOAD_FILE_SIZE_EXCEED(7001, "Upload file size exceed, max size is 5MB"),
    FILE_PATH_CREATE_FAILED(7002, "File path create failed, please check the file path"),
    DOWNLOAD_FILE_FAILED(7003, "Download file failed"),
    UPLOAD_FILE_FAILED(7004, "Upload file failed"),
    ;

    private final Integer code;
    private final String message;

    /**
     * Get the enumeration based on the response code
     *
     * @param code Response code
     * @return Enumeration
     */
    public static ResponseEnum getEnum(Integer code) {
        return Stream.of(ResponseEnum.values()).filter(e -> e.getCode().equals(code)).findFirst().orElse(null);
    }

}
