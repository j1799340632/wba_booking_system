package edu.unsw.core.config.exception;

import edu.unsw.core.enums.ResponseEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 19:34
 * @description Custom business exceptions
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessException extends RuntimeException {
    /**
     * Error code
     */
    private final Integer errorCode;

    /**
     * Error message
     */
    private final String errorMsg;

    public BusinessException() {
        super();
        this.errorCode = ResponseEnum.FAILURE.getCode();
        this.errorMsg = ResponseEnum.FAILURE.getMessage();
    }

    public BusinessException(ResponseEnum responseEnum) {
        super(responseEnum.getMessage());
        this.errorCode = responseEnum.getCode();
        this.errorMsg = responseEnum.getMessage();
    }

    public BusinessException(ResponseEnum responseEnum, Throwable cause) {
        super(responseEnum.getMessage(), cause);
        this.errorCode = responseEnum.getCode();
        this.errorMsg = responseEnum.getMessage();
    }

    public BusinessException(String errorMsg) {
        super(errorMsg);
        this.errorCode = ResponseEnum.FAILURE.getCode();
        this.errorMsg = errorMsg;
    }

    public BusinessException(String errorMsg, Throwable cause) {
        super(errorMsg, cause);
        this.errorCode = ResponseEnum.FAILURE.getCode();
        this.errorMsg = errorMsg;
    }

    public BusinessException(Integer errorCode, String errorMsg) {
        super(errorMsg);
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public BusinessException(Integer errorCode, String errorMsg, Throwable cause) {
        super(errorMsg, cause);
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

}
