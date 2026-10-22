package edu.unsw.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class BookingTimeConfig {
    @Bean
    public Clock bookingClock(@Value("${wba.booking.time-zone:Australia/Sydney}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
