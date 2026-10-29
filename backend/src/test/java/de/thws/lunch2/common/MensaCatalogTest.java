package de.thws.lunch2.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.domain.Campus;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MensaCatalogTest {
    @Test void eventWindowSkipsWeekendsAndCrossesMonthBoundaries() {
        var catalog = new MensaService(new ObjectMapper(), "2026-10-31", List.of("11:30", "12:15", "13:00"));
        List<String> dates = catalog.slots().stream().map(SlotService.Slot::date).distinct().sorted().toList();
        assertEquals(List.of("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06"), dates.subList(0, 5));
        assertEquals(SlotServiceTest.weekdays(java.time.LocalDate.parse("2026-10-31")).stream().map(Object::toString).toList(), dates);
    }
    @Test void forbidsStartingAtFoodCutoffAndAllowsEndingExactlyAtClosing() {
        var catalog = new MensaService(new ObjectMapper(), "2026-11-02", List.of("11:30", "12:15", "13:00"));
        var mensa = catalog.resolve(Campus.SCHWEINFURT, null);
        assertTrue(catalog.allows(mensa, "2026-11-06T13:15", false));
        assertFalse(catalog.allows(mensa, "2026-11-06T14:00", false));
        var studentenhaus = catalog.resolve(Campus.WUERZBURG, "WUERZBURG_STUDENTENHAUS");
        assertFalse(catalog.allows(studentenhaus, "2026-11-02T14:15", false));
    }
    @Test void legacyTimesRequireAnOmittedMensaAndMustStillFitServiceHours() {
        var catalog = new MensaService(new ObjectMapper(), "2026-11-02", List.of("08:00", "11:30", "14:00"));
        var mensa = catalog.resolve(Campus.SCHWEINFURT, null);
        assertTrue(catalog.allows(mensa, "2026-11-02T11:30", true));
        assertFalse(catalog.allows(mensa, "2026-11-02T11:30", false));
        assertFalse(catalog.allows(mensa, "2026-11-02T08:00", true));
        assertFalse(catalog.allows(mensa, "2026-11-06T14:00", true));
    }
}
