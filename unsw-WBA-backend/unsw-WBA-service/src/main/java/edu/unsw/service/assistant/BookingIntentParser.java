package edu.unsw.service.assistant;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingIntentParser {
    boolean available();
    BookingIntent parse(Context context);

    record Context(String message, List<String> history, LocalDateTime now, String timeZone,
                   List<String> hospitals, List<String> departments, List<String> types) { }
}
