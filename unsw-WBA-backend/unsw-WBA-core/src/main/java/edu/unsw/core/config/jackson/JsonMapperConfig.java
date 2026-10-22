package edu.unsw.core.config.jackson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

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
 * @date 2026/6/17 22:21
 * @description Because various tools (such as Redis) do not support LocalDateTime well, we need to configure time serialization. When using it, simply pass the Redisson serializer to new JsonMapper.myJsonMapper()
 */
@SpringBootConfiguration
public class JsonMapperConfig {
    private final Logger logger = LoggerFactory.getLogger(JsonMapperConfig.class);

    @Bean
    @Primary
    public JsonMapper myJsonMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();

        String pattern = "yyyy-MM-dd HH:mm:ss";
        TimeZone tz = TimeZone.getTimeZone(ZoneId.systemDefault());
        DateFormat df = new SimpleDateFormat(pattern);
        df.setTimeZone(tz);

        builder.defaultDateFormat(df);
        builder.defaultTimeZone(tz);
        logger.info("Set JsonMapper date formatting: {} -- {}", pattern, tz.getID());

        SimpleModule timeModule = new SimpleModule();

        // Serialization
        timeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(pattern)));
        timeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        timeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss")));
        timeModule.addSerializer(Long.class, BigNumberSerializer.MY_INSTANCE);
        timeModule.addSerializer(Long.TYPE, BigNumberSerializer.MY_INSTANCE);
        timeModule.addSerializer(BigInteger.class, BigNumberSerializer.MY_INSTANCE);
        timeModule.addSerializer(BigDecimal.class, ToStringSerializer.instance);

        // Deserialization
        timeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(pattern)));
        timeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        timeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern("HH:mm:ss")));

        builder.addModule(timeModule);

        return builder.build();
    }
}
