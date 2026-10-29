package de.thws.lunch2.common;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotServiceTest {
    /** Weekdays in the booking window [start, start + BOOKING_DAYS). */
    static List<LocalDate> weekdays(LocalDate start) {
        return start.datesUntil(start.plusDays(SlotService.BOOKING_DAYS))
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY).toList();
    }

    private static List<String> firstDates(SlotService service, int count) {
        return service.slots().stream().map(SlotService.Slot::date).distinct().limit(count).toList();
    }

    @Test
    void bookingWindowHasChronologicalWeekdaySlotsWith45MinuteDuration() {
        var service = new SlotService("2026-11-02", List.of("13:00", "11:30", "12:15"));
        List<LocalDate> days = weekdays(LocalDate.parse("2026-11-02"));
        assertEquals(days.size() * 3, service.slots().size());
        assertEquals("2026-11-02T11:30", service.slots().getFirst().id());
        assertEquals(days.getLast() + "T13:00", service.slots().getLast().id());
        List<String> ids = service.slots().stream().map(SlotService.Slot::id).toList();
        assertEquals(ids.stream().sorted().toList(), ids);
        assertEquals(new SlotService.Slot("2026-11-03T12:15", "2026-11-03", "12:15", "13:00",
                "Tue 3 Nov · 12:15"), service.findById("2026-11-03T12:15").orElseThrow());
        assertTrue(service.findById("2026-11-07T12:15").isEmpty(), "no weekend slots");
        assertTrue(service.slots().stream().map(s -> LocalDate.parse(s.date()).getDayOfWeek())
                .noneMatch(d -> d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY));
    }

    @Test
    void windowCoversAboutOneYearAndStopsAtItsEnd() {
        var service = new SlotService("2026-11-02", List.of("12:15"));
        LocalDate end = LocalDate.parse("2026-11-02").plusDays(SlotService.BOOKING_DAYS);
        assertTrue(LocalDate.parse(service.slots().getLast().date()).isBefore(end));
        assertTrue(service.slots().size() >= 52 * 5, "at least 52 weeks of weekdays");
        assertTrue(service.findById(end.plusDays(7) + "T12:15").isEmpty());
    }

    @Test
    void weekendStartSkipsToMonday() {
        var service = new SlotService("2026-11-07", List.of("12:15"));
        assertEquals(List.of("2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13"), firstDates(service, 5));
    }

    @Test
    void configuredStartCrossesWeekendsWithoutOfferingWeekendSlots() {
        var service = new SlotService("2026-11-06", List.of("12:15", "12:15"));
        assertEquals(List.of("2026-11-06", "2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12"), firstDates(service, 5));
        assertEquals(weekdays(LocalDate.parse("2026-11-06")).size(), service.slots().size(), "duplicate times are merged");
    }

    @Test
    void todayStartsAtTheCurrentBerlinDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Berlin"));
        var service = new SlotService("TODAY", List.of("12:15"));
        assertEquals(weekdays(today).getFirst().toString(), service.slots().getFirst().date());
    }
}
