package de.thws.lunch2.cards;

import de.thws.lunch2.cards.ConversationCardGenerator.GeneratedCard;
import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.domain.Card;
import de.thws.lunch2.domain.CardSet;
import de.thws.lunch2.domain.CardSource;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CardService {
    /** A table that got fallback cards tries the AI again at most this often. */
    static final Duration FALLBACK_RETRY = Duration.ofSeconds(60);
    /** New question sets per table and server run; keeps LLM usage (and cost) small. */
    static final int MAX_REFRESHES = 10;
    private static final ConversationCardGenerator NONE = new ConversationCardGenerator() {
        public List<GeneratedCard> generate(List<String> interestIds, String languageCode, List<String> avoid) { return List.of(); }
        public boolean available() { return false; }
    };

    private final GroupRepository groups;
    private final ParticipantRepository participants;
    private final ConversationCardGenerator generator;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> nextAiRetry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> refreshes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> seen = new ConcurrentHashMap<>();

    @Inject
    public CardService(GroupRepository groups, ParticipantRepository participants, GeminiCardGenerator generator) {
        this(groups, participants, generator, Clock.systemUTC());
    }

    public CardService(GroupRepository groups) {
        this(groups, null, NONE, Clock.systemUTC());
    }

    CardService(GroupRepository groups, ConversationCardGenerator generator) {
        this(groups, null, generator, Clock.systemUTC());
    }

    CardService(GroupRepository groups, ParticipantRepository participants, ConversationCardGenerator generator, Clock clock) {
        this.groups = groups;
        this.participants = participants;
        this.generator = generator;
        this.clock = clock;
    }

    public CardsResponse get(String groupId, String participantId) {
        requireMember(groupId, participantId);
        synchronized (locks.computeIfAbsent(groupId, ignored -> new Object())) {
            LunchGroup group = requireMember(groupId, participantId);
            CardSet cached = group.cards();
            if (cached != null && !shouldRetryAi(group)) {
                Log.info("Conversation cards served from cache");
                return response(groupId, cached);
            }
            // First visit, or cached fallback cards while the AI is configured again: try to (re)generate.
            String language = language(group);
            Optional<CardSet> ai = tryAi(group, language, List.of());
            if (cached != null && ai.isEmpty()) return response(groupId, cached);
            CardSet cards = ai.orElseGet(() -> new CardSet(language, CardSource.FALLBACK,
                    FallbackCards.pick(group.id(), group.sharedInterests(), language)));
            return save(groupId, participantId, cards);
        }
    }

    /** A new AI question set for the table that avoids questions it has already seen. */
    public CardsResponse refresh(String groupId, String participantId) {
        requireMember(groupId, participantId);
        synchronized (locks.computeIfAbsent(groupId, ignored -> new Object())) {
            LunchGroup group = requireMember(groupId, participantId);
            if (!generator.available()) {
                throw new ApiException(503, "CARDS_UNAVAILABLE", "New questions need the AI service, which is not configured.");
            }
            if (refreshes.getOrDefault(groupId, 0) >= MAX_REFRESHES) {
                throw new ApiException(429, "CARDS_LIMIT", "Your table has used all new question sets for now.");
            }
            if (group.cards() != null) remember(groupId, group.cards().cards());
            List<String> avoid = List.copyOf(seen.getOrDefault(groupId, Set.of()));
            CardSet cards = tryAi(group, language(group), avoid).orElseThrow(() ->
                    new ApiException(503, "CARDS_UNAVAILABLE", "New questions could not be created right now. Please try again."));
            refreshes.merge(groupId, 1, Integer::sum);
            return save(groupId, participantId, cards);
        }
    }

    private boolean shouldRetryAi(LunchGroup group) {
        return group.cards().source() == CardSource.FALLBACK && generator.available()
                && !clock.instant().isBefore(nextAiRetry.getOrDefault(group.id(), Instant.EPOCH));
    }

    private CardsResponse save(String groupId, String participantId, CardSet cards) {
        // Re-read before saving so a deleted group is not recreated or other fields overwritten.
        LunchGroup latest = requireMember(groupId, participantId);
        groups.save(new LunchGroup(latest.id(), latest.campus(), latest.slotId(), latest.location(), latest.memberIds(),
                latest.sharedInterests(), latest.commonLanguages(), latest.whyThisGroup(), latest.score(), latest.createdAt(), cards));
        remember(groupId, cards.cards());
        Log.infof("Conversation cards cached with source %s", cards.source());
        return response(groupId, cards);
    }

    private Optional<CardSet> tryAi(LunchGroup group, String language, List<String> avoid) {
        if (!generator.available()) return Optional.empty();
        try {
            List<GeneratedCard> generated = generator.generate(interests(group), language, avoid);
            Set<String> avoided = new LinkedHashSet<>(avoid.stream().map(CardService::key).toList());
            Map<String, GeneratedCard> unique = new LinkedHashMap<>();
            for (GeneratedCard card : generated == null ? List.<GeneratedCard>of() : generated) {
                if (card == null || card.text() == null) return failed(group);
                String text = card.text().trim();
                if (text.isEmpty() || text.length() > 140) return failed(group);
                if (!avoided.contains(key(text))) unique.putIfAbsent(key(text), new GeneratedCard(text, card.topic()));
            }
            if (generated == null || generated.size() < 3 || generated.size() > 6 || unique.size() < 3
                    || (avoid.isEmpty() && unique.size() != generated.size())) return failed(group);
            List<Card> cards = new ArrayList<>();
            for (GeneratedCard card : unique.values()) {
                if (cards.size() == 5) break;
                cards.add(new Card("c" + (cards.size() + 1), card.text(), card.topic() == null ? "generic" : card.topic()));
            }
            for (Card card : FallbackCards.pick(group.id(), group.sharedInterests(), language)) {
                if (cards.size() == 5) break;
                if (cards.stream().noneMatch(existing -> key(existing.text()).equals(key(card.text())))) {
                    cards.add(new Card("c" + (cards.size() + 1), card.text(), card.topic()));
                }
            }
            nextAiRetry.remove(group.id());
            return Optional.of(new CardSet(language, CardSource.AI, List.copyOf(cards)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Log.warn("Card generation interrupted; using fallback cards");
        } catch (Exception exception) {
            // Do not log provider response bodies, prompts or credentials.
            Log.warnf("Card generation unavailable (%s); using fallback cards", exception.getClass().getSimpleName());
        }
        return failed(group);
    }

    private Optional<CardSet> failed(LunchGroup group) {
        nextAiRetry.put(group.id(), clock.instant().plus(FALLBACK_RETRY));
        return Optional.empty();
    }

    /** Shared interests first, then every other interest at the table by how many members hold it. */
    private List<String> interests(LunchGroup group) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>(group.sharedInterests());
        if (participants != null) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String id : group.memberIds()) {
                participants.findById(id).map(Participant::interests).orElse(List.of())
                        .forEach(interest -> counts.merge(interest, 1, Integer::sum));
            }
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                    .forEach(entry -> ordered.add(entry.getKey()));
        }
        return List.copyOf(ordered);
    }

    private void remember(String groupId, List<Card> cards) {
        seen.computeIfAbsent(groupId, ignored -> new LinkedHashSet<>()).addAll(cards.stream().map(Card::text).toList());
    }

    private static String language(LunchGroup group) {
        return group.commonLanguages().contains("en") ? "en" : group.commonLanguages().getFirst();
    }

    private static String key(String text) {
        return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private LunchGroup requireMember(String groupId, String participantId) {
        LunchGroup group = groups.findById(groupId)
                .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Group not found."));
        if (!group.memberIds().contains(participantId)) {
            throw new ApiException(403, "FORBIDDEN", "Only members can view this group's cards.");
        }
        return group;
    }

    private static CardsResponse response(String groupId, CardSet cards) {
        return new CardsResponse(groupId, cards.language(), cards.source(), cards.cards());
    }
}
