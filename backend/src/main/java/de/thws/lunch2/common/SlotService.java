package de.thws.lunch2.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class SlotService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private final List<Slot> slots;

    @Inject
    public SlotService(@ConfigProperty(name = "lunch2.event-start-date") String eventStartDate,
                       @ConfigProperty(name = "lunch2.slot-times") List<String> slotTimes) {
        var times = slotTimes.stream().map(String::trim).map(LocalTime::parse).distinct().sorted().toList();
        if (times.isEmpty()) {
            throw new IllegalArgumentException("At least one lunch slot time must be configured");
        }
        List<Slot> generated = new ArrayList<>();
        LocalDate date = LocalDate.parse(eventStartDate);
        int weekdays = 0;
        while (weekdays < 5) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                for (LocalTime time : times) {
                    String start = time.format(TIME);
                    generated.add(new Slot(date + "T" + start, date.toString(), start,
                            time.plusMinutes(45).format(TIME), date.format(LABEL_DATE) + " · " + start));
                }
                weekdays++;
            }
            date = date.plusDays(1);
        }
        slots = List.copyOf(generated);
    }

    public List<Slot> slots() {
        return slots;
    }

    public Optional<Slot> findById(String id) {
        return slots.stream().filter(slot -> slot.id().equals(id)).findFirst();
    }

    public record Slot(String id, String date, String start, String end, String label) {}
}
