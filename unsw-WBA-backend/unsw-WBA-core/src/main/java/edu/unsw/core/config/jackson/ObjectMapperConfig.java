package edu.unsw.core.config.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 17/6/2026 22:34
 * @description Because various tools (such as Redis) do not support LocalDateTime well, we need to configure time serialization. When using it, simply pass the Redisson serializer to new ObjectMapper.myObjectMapper()
 */
public class ObjectMapperConfig {
    private final Logger logger = LoggerFactory.getLogger(ObjectMapperConfig.class);

    public ObjectMapper myObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        String pattern = "yyyy-MM-dd HH:mm:ss";
        TimeZone tz = TimeZone.getTimeZone(ZoneId.systemDefault());
        // This section describes the formatting settings for the Date type
        DateFormat df = new SimpleDateFormat(pattern);
        df.setTimeZone(tz);
        objectMapper.setDateFormat(df);
        logger.info("Set ObjectMapper date formatting: {} -- {}", pattern, tz.getID());

        // Solving the problem of Jackson2 failing to deserialize LocalDateTime
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // Serialization
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(pattern)));
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss")));
        javaTimeModule.addSerializer(Long.class, ToStringSerializer.instance);
        javaTimeModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        javaTimeModule.addSerializer(BigInteger.class, ToStringSerializer.instance);
        javaTimeModule.addSerializer(BigDecimal.class, ToStringSerializer.instance);

        // Deserialization
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(pattern)));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern("HH:mm:ss")));

        objectMapper.registerModule(javaTimeModule);

        return objectMapper;
    }
}
