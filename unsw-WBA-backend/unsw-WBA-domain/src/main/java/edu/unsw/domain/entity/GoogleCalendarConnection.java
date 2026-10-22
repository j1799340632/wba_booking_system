package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Stanley
 * @date 26/7/2026
 * @description Google Calendar connection information
 */
@Data
@TableName("google_calendar_connection")
public class GoogleCalendarConnection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String googleEmail;

    private String refreshToken;

    private String accessToken;

    private LocalDateTime accessTokenExpiry;

    private Boolean isEnable;

    private Long creator;

    private Long updater;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}