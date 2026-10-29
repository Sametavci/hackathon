package de.thws.lunch2.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.common.SlotService.Slot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class MensaService {
    public record Hours(int dayOfWeek, String opens, String lunchStart, String foodUntil, String closes) {}
    public record Mensa(String id, Campus campus, String name, String address, boolean defaultForCampus,
                        List<Hours> hours, String sourceUrl, String verifiedOn, List<String> slotIds) {}
    private final List<Mensa> mensas;
    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final List<String> legacyTimes;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    @Inject
    public MensaService(ObjectMapper mapper,
            @ConfigProperty(name = "lunch2.event-start-date") String startDate,
            @ConfigProperty(name = "lunch2.slot-times") List<String> legacyTimes) {
        this.legacyTimes = List.copyOf(legacyTimes);
        List<Mensa> rows;
        try (var stream = MensaService.class.getResourceAsStream("/catalog/mensas.json")) {
            if (stream == null) throw new IllegalStateException("Mensa catalog missing");
            rows = mapper.readValue(stream, new TypeReference<List<Mensa>>() {});
        } catch (IOException e) { throw new UncheckedIOException(e); }
        List<LocalDate> days = new ArrayList<>();
        LocalDate date = LocalDate.parse(startDate);
        while (days.size() < 5) {
            if (date.getDayOfWeek().getValue() <= 5) days.add(date);
            date = date.plusDays(1);
        }
        for (LocalDate day : days) for (String time : legacyTimes) addSlot(day, LocalTime.parse(time));
        List<Mensa> generated = new ArrayList<>();
        for (Mensa row : rows) {
            List<String> ids = new ArrayList<>();
            for (LocalDate day : days) {
                Hours hours = row.hours().stream().filter(h -> h.dayOfWeek() == day.getDayOfWeek().getValue()).findFirst().orElse(null);
                if (hours == null) continue;
                for (LocalTime time = LocalTime.parse(hours.lunchStart());
                     time.isBefore(LocalTime.parse(hours.foodUntil())) && !time.plusMinutes(45).isAfter(LocalTime.parse(hours.closes()));
                     time = time.plusMinutes(45)) ids.add(addSlot(day, time));
            }
            generated.add(new Mensa(row.id(), row.campus(), row.name(), row.address(), row.defaultForCampus(),
                    List.copyOf(row.hours()), row.sourceUrl(), row.verifiedOn(), List.copyOf(ids)));
        }
        mensas = List.copyOf(generated);
    }
    private String addSlot(LocalDate date, LocalTime time) {
        String start = TIME.format(time), id = date + "T" + start;
        slots.put(id, new Slot(id, date.toString(), start, TIME.format(time.plusMinutes(45)), DAY.format(date) + " · " + start));
        return id;
    }
    public List<Mensa> mensas() { return mensas; }
    public List<Slot> slots() { return slots.values().stream().sorted(java.util.Comparator.comparing(Slot::id)).toList(); }
    public Slot slot(String id) {
        Slot slot = slots.get(id);
        if (slot == null) throw new ApiException(400, "UNKNOWN_SLOT", "Choose an available lunch slot.");
        return slot;
    }
    public Mensa resolve(Campus campus, String id) {
        return mensas.stream().filter(m -> m.campus() == campus && (id == null ? m.defaultForCampus() : m.id().equals(id)))
                .findFirst().orElseThrow(() -> new ApiException(400, "VALIDATION_FAILED", "Choose a Mensa on your campus."));
    }
    public boolean allows(Mensa mensa, String slotId, boolean legacy) {
        if (mensa.slotIds().contains(slotId)) return true;
        Slot slot = slots.get(slotId);
        if (!legacy || slot == null || !legacyTimes.contains(slot.start())) return false;
        return mensa.hours().stream().filter(h -> h.dayOfWeek() == LocalDate.parse(slot.date()).getDayOfWeek().getValue())
                .anyMatch(h -> slot.start().compareTo(h.lunchStart()) >= 0 && slot.start().compareTo(h.foodUntil()) < 0 && slot.end().compareTo(h.closes()) <= 0);
    }
    public String location(Mensa mensa) { return mensa.name() + " · " + mensa.address(); }
}
