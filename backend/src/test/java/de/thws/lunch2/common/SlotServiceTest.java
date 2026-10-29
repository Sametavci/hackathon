package de.thws.lunch2.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotServiceTest {
    @Test
    void defaultWeekHasChronologicalSlotsAnd45MinuteDuration() {
        var service = new SlotService("2026-11-02", List.of("13:00", "11:30", "12:15"));
        assertEquals(15, service.slots().size());
        assertEquals("2026-11-02T11:30", service.slots().getFirst().id());
        assertEquals("2026-11-06T13:00", service.slots().getLast().id());
        assertEquals(new SlotService.Slot("2026-11-03T12:15", "2026-11-03", "12:15", "13:00",
                "Tue 3 Nov · 12:15"), service.findById("2026-11-03T12:15").orElseThrow());
        assertTrue(service.findById("2026-11-07T12:15").isEmpty());
    }

    @Test
    void weekendStartSkipsToMondayAndIncludesFiveWeekdays() {
        var service = new SlotService("2026-11-07", List.of("12:15"));
        assertEquals(List.of("2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13"),
                service.slots().stream().map(SlotService.Slot::date).toList());
    }

    @Test
    void configuredWeekCrossesWeekendWithoutOfferingWeekendSlots() {
        var service = new SlotService("2026-11-06", List.of("12:15", "12:15"));
        assertEquals(List.of("2026-11-06", "2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12"),
                service.slots().stream().map(SlotService.Slot::date).toList());
    }
}
