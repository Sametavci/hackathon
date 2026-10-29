package de.thws.lunch2.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.auth.TokenService;
import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.common.CatalogService;
import de.thws.lunch2.common.SlotService;
import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SeedService {
    private final ParticipantRepository participants;
    private final GroupRepository groups;
    private final FriendPairRepository pairs;
    private final TokenService tokens;
    private final CatalogService catalog;
    private final SlotService slots;
    private final ObjectMapper mapper;
    private final boolean demoMode;
    private final String publicBaseUrl;

    @Inject
    public SeedService(ParticipantRepository participants, GroupRepository groups, FriendPairRepository pairs,
            TokenService tokens, CatalogService catalog, SlotService slots, ObjectMapper mapper,
            @ConfigProperty(name = "lunch2.demo-mode") boolean demoMode,
            @ConfigProperty(name = "lunch2.public-base-url") String publicBaseUrl) {
        this.participants = participants;
        this.groups = groups;
        this.pairs = pairs;
        this.tokens = tokens;
        this.catalog = catalog;
        this.slots = slots;
        this.mapper = mapper;
        this.demoMode = demoMode;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public synchronized SeedResponse seed() {
        if (!demoMode) throw new ApiException(403, "FORBIDDEN", "Demo seeding is disabled.");
        if (!participants.findAll().isEmpty() || !groups.findAll().isEmpty() || !pairs.findAll().isEmpty()) {
            throw new ApiException(409, "DEMO_NOT_EMPTY", "Reset the demo data before seeding again.");
        }
        List<Template> templates = loadTemplates();
        Map<String, Template> byKey = new LinkedHashMap<>();
        Map<String, String> ids = new HashMap<>();
        for (Template template : templates) {
            if (byKey.putIfAbsent(template.key(), template) != null) throw new IllegalStateException("Duplicate seed key");
            ids.put(template.key(), UUID.randomUUID().toString());
        }
        Map<String, String> pairIds = new HashMap<>();
        List<FriendPair> plannedPairs = new ArrayList<>();
        for (Template template : templates) {
            if (template.friendKey() == null) continue;
            Template friend = byKey.get(template.friendKey());
            if (friend == null || !template.key().equals(friend.friendKey()) || friend.key().equals(template.key())) {
                throw new IllegalStateException("Seed friend references must be reciprocal");
            }
            if (!pairIds.containsKey(template.key())) {
                String pairId = UUID.randomUUID().toString();
                pairIds.put(template.key(), pairId);
                pairIds.put(friend.key(), pairId);
                // Keep the deliberately cross-campus demo pair active so the engine's pre-check explains it.
                plannedPairs.add(new FriendPair(pairId, ids.get(template.key()), ids.get(friend.key()),
                        PairStatus.ACTIVE, null));
            }
        }
        List<String> dates = slots.slots().stream().map(SlotService.Slot::date).distinct().toList();
        List<Participant> planned = new ArrayList<>();
        List<SeedResponse.DemoParticipant> response = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < templates.size(); i++) {
            Template template = templates.get(i);
            if (template.dayIndex() < 0 || template.dayIndex() >= dates.size()
                    || template.languages().isEmpty() || template.languages().stream().anyMatch(code -> !catalog.hasLanguage(code))
                    || template.interests().isEmpty() || template.interests().stream().anyMatch(id -> !catalog.hasInterest(id))
                    || (template.studyField() != null && !catalog.hasStudyField(template.studyField()))) {
                throw new IllegalStateException("Invalid seed catalog values");
            }
            String slotId = dates.get(template.dayIndex()) + "T" + template.time();
            if (slots.findById(slotId).isEmpty()) throw new IllegalStateException("Seed requires configured 11:30, 12:15 and 13:00 slots");
            String token = tokens.newToken();
            String id = ids.get(template.key());
            planned.add(new Participant(id, tokens.hash(token), template.displayName(), template.category(), template.campus(),
                    template.languages(), template.interests(), List.of(slotId), template.studyField(), MatchStatus.WAITING,
                    null, ReasonCode.WAITING_FOR_NEXT_ROUND, pairIds.get(template.key()), now.plusMillis(i), now));
            response.add(new SeedResponse.DemoParticipant(id, template.displayName(), template.campus(), template.category(),
                    publicBaseUrl + "/#/back?t=" + token));
        }
        // Prepare and validate the complete dataset before the first persistent change.
        planned.forEach(participants::save);
        plannedPairs.forEach(pairs::save);
        Log.infof("Demo seed created %d participants and %d friend pairs", planned.size(), plannedPairs.size());
        return new SeedResponse(List.copyOf(response));
    }

    private List<Template> loadTemplates() {
        try (var stream = SeedService.class.getResourceAsStream("/seed/demo-participants.json")) {
            if (stream == null) throw new IllegalStateException("Demo seed resource is missing");
            return mapper.readValue(stream, new TypeReference<List<Template>>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read demo seed resource", exception);
        }
    }

    public record Template(String key, String displayName, Category category, Campus campus, List<String> languages,
            List<String> interests, int dayIndex, String time, String studyField, String friendKey) {}
}
