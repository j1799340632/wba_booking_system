package edu.unsw.core.domain.pojo;

import edu.unsw.core.enums.ResponseEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/17 18:27
 * @description Universal return body
 */
@Data
@Schema(title = "Universal return body")
public class ResultEntity<T> {
    @Schema(description = "Response code returned to the front end")
    private Integer code;
    @Schema(description = "Information returned to the front end")
    private String message;
    @Schema(description = "Returned data")
    private T data;

    public ResultEntity(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public ResultEntity(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public ResultEntity(ResponseEnum responseEnum) {
        this.code = responseEnum.getCode();
        this.message = responseEnum.getMessage();
    }

    public ResultEntity(ResponseEnum responseEnum, T data) {
        this.code = responseEnum.getCode();
        this.message = responseEnum.getMessage();
        this.data = data;
    }

    public static <T> ResultEntity<T> success() {
        return new ResultEntity<>(ResponseEnum.SUCCESS);
    }

    public static <T> ResultEntity<T> success(String message) {
        ResultEntity<T> resultEntity = success();
        resultEntity.setMessage(message);
        return resultEntity;
    }

    public static <T> ResultEntity<T> success(String message, T data) {
        ResultEntity<T> resultEntity = success();
        resultEntity.setMessage(message);
        resultEntity.setData(data);
        return resultEntity;
    }

    public static <T> ResultEntity<T> failure() {
        return new ResultEntity<>(ResponseEnum.FAILURE);
    }

    public static <T> ResultEntity<T> failure(ResponseEnum responseEnum) {
        return new ResultEntity<>(responseEnum);
    }

    public static <T> ResultEntity<T> failure(String message) {
        ResultEntity<T> resultEntity = failure();
        resultEntity.setMessage(message);
        return resultEntity;
    }

    public static <T> ResultEntity<T> failure(Integer code, String message) {
        return new ResultEntity<>(code, message);
    }

}
