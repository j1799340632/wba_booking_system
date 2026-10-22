package edu.unsw.core.config.jackson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jackson.JacksonComponent;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
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
 * @author 徐一杰
 * @date 2026/6/17 18:37
 * @description 全局日期格式化，yml的jackson配置只对date类型生效，Calendar和LocalDateTime都无效，所以这里要写个配置文件
 */
@SpringBootConfiguration
@JacksonComponent
public class JacksonConfig {
    private final Logger logger = LoggerFactory.getLogger(JacksonConfig.class);

    @Value("${spring.jackson.date-format:yyyy-MM-dd HH:mm:ss}")
    private String pattern;

    @Value("${spring.jackson.time-zone:GMT+10}")
    private String timeZone;

    /**
     * Global configuration serialization returns JSON processing
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        TimeZone tz = TimeZone.getTimeZone(ZoneId.systemDefault());
        DateFormat df = new SimpleDateFormat(pattern);
        df.setTimeZone(tz);
        logger.info("Set global date formatting: {}--{}", pattern, tz.getID());

        return builder -> {
            builder.defaultTimeZone(TimeZone.getDefault());
            builder.defaultDateFormat(df);

            // Format the Date type
            builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);

            SimpleModule customModule = new SimpleModule();

            // Serialization of numeric types
            customModule.addSerializer(Long.class, BigNumberSerializer.MY_INSTANCE);
            customModule.addSerializer(Long.TYPE, BigNumberSerializer.MY_INSTANCE);
            customModule.addSerializer(BigInteger.class, BigNumberSerializer.MY_INSTANCE);
            customModule.addSerializer(BigDecimal.class, ToStringSerializer.instance);

            // Serialization of time types
            customModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(pattern)));
            customModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            customModule.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss")));

            // Deserialization of time types
            customModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(pattern)));
            customModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            customModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern("HH:mm:ss")));

            builder.addModule(customModule);
        };
    }

}
