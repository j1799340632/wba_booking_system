package edu.unsw.core.config.exception;

import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.core.enums.ResponseEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;

/**
 * @author 徐一杰
 * @date 2026/6/17 19:36
 * @description Custom global exception handling
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResultEntity<Object> concurrentBooking(ConcurrencyFailureException error) {
        return ResultEntity.failure(ResponseEnum.SLOT_CONFLICT.getCode(),
                "The session is busy or changed. Retry the same confirmation or refresh the search.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResultEntity<Object> unavailableDatabase(DataAccessException error) {
        logger.error("Database request unavailable: {}", error.getClass().getSimpleName());
        return ResultEntity.failure(ResponseEnum.DATA_SAVE_FAILED.getCode(),
                "The database is temporarily unavailable. Retry the same request or check My Bookings.");
    }

    /**
     * Handling custom business exceptions
     *
     * @param request Request information
     * @param e       Custom business exceptions
     * @return Custom return body
     */
    @ExceptionHandler(value = BusinessException.class)
    public ResultEntity<Object> bizExceptionHandler(HttpServletRequest request, BusinessException e) {
        logger.error("Business exception: {}", e.getErrorMsg());
        return ResultEntity.failure(e.getErrorCode(), e.getErrorMsg());
    }

    /**
     * Handling entity class field verification exceptions
     *
     * @param request Request information
     * @param e       Entity class field validation error
     * @return Custom return body
     */
    @ExceptionHandler(value = BindException.class)
    public ResultEntity<Object> bizExceptionHandler(HttpServletRequest request, BindException e) {
        String exceptionString = e.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        logger.error("Entity class field parameter validation failed: {}", exceptionString);
        return ResultEntity.failure(ResponseEnum.ILLEGAL_PARAM.getCode(), exceptionString);
    }

    /**
     * Handling parameter validation exceptions
     *
     * @param request Request information
     * @param e       The param parameter validation failed
     * @return Custom return body
     */
    @ExceptionHandler(value = ValidationException.class)
    public ResultEntity<Object> bizExceptionHandler(HttpServletRequest request, ValidationException e) {
        String exceptionString = e.getMessage();
        logger.error("Param parameter validation failed: {}", exceptionString);
        return ResultEntity.failure(ResponseEnum.ILLEGAL_PARAM.getCode(), exceptionString);
    }

    /**
     * Handling file upload size exceeded exceptions
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResultEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        String exceptionString = e.getMessage();
        logger.warn("Upload file size exceeded: {}", exceptionString);
        return ResultEntity.failure(ResponseEnum.UPLOAD_FILE_SIZE_EXCEED.getCode(), exceptionString);
    }

}
