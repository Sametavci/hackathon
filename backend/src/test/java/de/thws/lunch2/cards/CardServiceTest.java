package de.thws.lunch2.cards;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Card;
import de.thws.lunch2.domain.CardSet;
import de.thws.lunch2.domain.CardSource;
import de.thws.lunch2.cards.ConversationCardGenerator.GeneratedCard;
import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardServiceTest {
    private static final List<String> QUESTIONS = List.of("Which game would you suggest?", "What would you like to explore?",
            "What music fits this lunch?", "Which skill would you try?", "What makes a good study break?");
    @Test
    void concurrentFirstRequestsPersistExactlyOneCardSet() throws Exception {
        FakeGroups repository = new FakeGroups(null);
        AtomicInteger callsMade = new AtomicInteger();
        CardService service = new CardService(repository, (interests, language, avoid) -> {
            callsMade.incrementAndGet();
            return cards(QUESTIONS);
        });
        CyclicBarrier ready = new CyclicBarrier(8);
        List<Callable<CardsResponse>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) calls.add(() -> {
            ready.await(5, TimeUnit.SECONDS);
            return service.get("group", "member");
        });
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = executor.invokeAll(calls, 10, TimeUnit.SECONDS);
            CardsResponse first = results.getFirst().get();
            for (var result : results) assertEquals(first, result.get());
            assertEquals(1, repository.saves);
            assertEquals(1, callsMade.get());
            assertEquals(CardSource.AI, first.source());
        }
        // Cache survives a fresh service instance, since it belongs to the repository's saved group.
        CardsResponse restored = new CardService(repository).get("group", "member");
        assertEquals(repository.value.cards().cards(), restored.cards());
        assertEquals(1, repository.saves);
    }

    @Test
    void successfulGeneratorReceivesOnlyInterestIdsAndLanguageAndIsCached() {
        FakeGroups repository = new FakeGroups(null);
        CardService service = new CardService(repository, (interests, language, avoid) -> {
            assertEquals(List.of("travel"), interests);
            assertEquals("en", language);
            assertEquals(List.of(), avoid);
            return cards(QUESTIONS);
        });
        assertEquals(CardSource.AI, service.get("group", "member").source());
        assertEquals(QUESTIONS, repository.value.cards().cards().stream().map(Card::text).toList());
    }

    @Test
    void throwingGeneratorFallsBackAndIsNotRetriedWithinTheCooldown() {
        FakeGroups repository = new FakeGroups(null);
        AtomicInteger attempts = new AtomicInteger();
        CardService service = new CardService(repository, (interests, language, avoid) -> {
            attempts.incrementAndGet();
            throw new java.io.IOException("Simulated provider failure");
        });
        CardsResponse first = service.get("group", "member");
        assertEquals(CardSource.FALLBACK, first.source());
        assertEquals(first, service.get("group", "member"));
        assertEquals(1, attempts.get());
    }

    @Test
    void invalidQuestionSetsUseFallback() {
        for (List<String> invalid : List.of(List.of("one", "two"), List.of("", "two", "three"),
                List.of("a".repeat(141), "two", "three"), List.of("same", "same", "third"),
                List.of("1", "2", "3", "4", "5", "6", "7"))) {
            FakeGroups repository = new FakeGroups(null);
            assertEquals(CardSource.FALLBACK, new CardService(repository, (interests, lang, avoid) -> cards(invalid))
                    .get("group", "member").source());
        }
    }

    @Test
    void threeFourOrSixValidQuestionsAreNormalizedToExactlyFiveCards() {
        for (int count : List.of(3, 4, 6)) {
            List<String> generated = java.util.stream.IntStream.range(0, count).mapToObj(i -> "Question " + i + "?").toList();
            CardsResponse result = new CardService(new FakeGroups(null), (interests, lang, avoid) -> cards(generated)).get("group", "member");
            assertEquals(CardSource.AI, result.source());
            assertEquals(5, result.cards().size());
            assertEquals(generated.subList(0, Math.min(5, count)),
                    result.cards().stream().limit(Math.min(5, count)).map(Card::text).toList());
        }
    }

    @Test
    void cachedAiCardsAreReturnedWithoutDowngradingOrRewritingThem() {
        CardSet cached = new CardSet("en", CardSource.AI, List.of(
                new Card("c1", "First cached question?", "generic"), new Card("c2", "Second cached question?", "generic"),
                new Card("c3", "Third cached question?", "generic"), new Card("c4", "Fourth cached question?", "generic"),
                new Card("c5", "Fifth cached question?", "generic")));
        FakeGroups repository = new FakeGroups(cached);
        CardsResponse result = new CardService(repository).get("group", "member");
        assertEquals(CardSource.AI, result.source());
        assertEquals(cached.cards(), result.cards());
        assertEquals(0, repository.saves);
    }

    @Test
    void cachedFallbackCardsAreUpgradedToAiOnceTheProviderWorks() {
        FakeGroups repository = new FakeGroups(fallbackSet());
        CardsResponse result = new CardService(repository, (interests, lang, avoid) -> cards(QUESTIONS)).get("group", "member");
        assertEquals(CardSource.AI, result.source());
        assertEquals(QUESTIONS, result.cards().stream().map(Card::text).toList());
        assertEquals(1, repository.saves);
    }

    @Test
    void failedUpgradeKeepsCachedFallbackAndRetriesOnlyAfterTheCooldown() {
        FakeGroups repository = new FakeGroups(fallbackSet());
        MutableClock clock = new MutableClock();
        AtomicInteger attempts = new AtomicInteger();
        CardService service = new CardService(repository, null, (interests, lang, avoid) -> {
            attempts.incrementAndGet();
            throw new java.io.IOException("down");
        }, clock);
        assertEquals(fallbackSet().cards(), service.get("group", "member").cards());
        service.get("group", "member");
        assertEquals(1, attempts.get());
        clock.advance(CardService.FALLBACK_RETRY.plusSeconds(1));
        service.get("group", "member");
        assertEquals(2, attempts.get());
        assertEquals(0, repository.saves);
    }

    @Test
    void withoutProviderCachedFallbackIsServedAndNeverRetried() {
        FakeGroups repository = new FakeGroups(fallbackSet());
        assertEquals(CardSource.FALLBACK, new CardService(repository).get("group", "member").source());
        assertEquals(0, repository.saves);
    }

    @Test
    void everyMembersInterestsAreSentSharedFirstThenByHowManyHoldThem() {
        FakeParticipants people = new FakeParticipants(Map.of(
                "member", List.of("travel", "music", "cooking"), "other", List.of("travel", "music"),
                "third", List.of("travel", "anime"), "fourth", List.of("hiking", "music")));
        AtomicReference<List<String>> sent = new AtomicReference<>();
        new CardService(new FakeGroups(null), people, (interests, lang, avoid) -> {
            sent.set(interests);
            return cards(QUESTIONS);
        }, Clock.systemUTC()).get("group", "member");
        assertEquals(List.of("travel", "music", "anime", "cooking", "hiking"), sent.get());
    }

    @Test
    void generatedTopicsAreKeptOnTheCards() {
        List<GeneratedCard> generated = List.of(new GeneratedCard("Best trip?", "travel"), new GeneratedCard("Best game?", "gaming"),
                new GeneratedCard("Best song?", "music"), new GeneratedCard("Best snack?", "generic"), new GeneratedCard("Best view?", "hiking"));
        CardsResponse result = new CardService(new FakeGroups(null), (interests, lang, avoid) -> generated).get("group", "member");
        assertEquals(List.of("travel", "gaming", "music", "generic", "hiking"), result.cards().stream().map(Card::topic).toList());
    }

    @Test
    void refreshAsksForNewQuestionsAvoidingSeenOnesAndReplacesTheCache() {
        FakeGroups repository = new FakeGroups(null);
        List<String> second = List.of("New one?", "New two?", "New three?", "New four?", "New five?");
        AtomicReference<List<String>> avoided = new AtomicReference<>();
        CardService service = new CardService(repository, (interests, lang, avoid) -> {
            avoided.set(avoid);
            return cards(avoid.isEmpty() ? QUESTIONS : second);
        });
        service.get("group", "member");
        CardsResponse refreshed = service.refresh("group", "member");
        assertTrue(avoided.get().containsAll(QUESTIONS));
        assertEquals(second, refreshed.cards().stream().map(Card::text).toList());
        assertEquals(second, repository.value.cards().cards().stream().map(Card::text).toList());
    }

    @Test
    void refreshDropsQuestionsTheTableAlreadySaw() {
        FakeGroups repository = new FakeGroups(null);
        CardService service = new CardService(repository, (interests, lang, avoid) -> avoid.isEmpty() ? cards(QUESTIONS)
                : cards(List.of(QUESTIONS.get(0), "Fresh A?", "Fresh B?", "Fresh C?", QUESTIONS.get(1))));
        service.get("group", "member");
        List<String> texts = service.refresh("group", "member").cards().stream().map(Card::text).toList();
        assertEquals(List.of("Fresh A?", "Fresh B?", "Fresh C?"), texts.subList(0, 3));
        assertFalse(texts.contains(QUESTIONS.get(0)));
        assertEquals(5, texts.size());
    }

    @Test
    void refreshIsLimitedPerTable() {
        AtomicInteger round = new AtomicInteger();
        CardService service = new CardService(new FakeGroups(null), (interests, lang, avoid) -> {
            int n = round.incrementAndGet();
            return cards(List.of("Q" + n + "a?", "Q" + n + "b?", "Q" + n + "c?", "Q" + n + "d?", "Q" + n + "e?"));
        });
        for (int i = 0; i < CardService.MAX_REFRESHES; i++) service.refresh("group", "member");
        ApiException limit = assertThrows(ApiException.class, () -> service.refresh("group", "member"));
        assertEquals("CARDS_LIMIT", limit.code());
    }

    @Test
    void refreshFailsClearlyWithoutProviderOrWhenGenerationFailsAndKeepsTheCache() {
        FakeGroups repository = new FakeGroups(fallbackSet());
        ApiException missing = assertThrows(ApiException.class, () -> new CardService(repository).refresh("group", "member"));
        assertEquals("CARDS_UNAVAILABLE", missing.code());
        ApiException failed = assertThrows(ApiException.class, () -> new CardService(repository, (interests, lang, avoid) -> {
            throw new java.io.IOException("down");
        }).refresh("group", "member"));
        assertEquals("CARDS_UNAVAILABLE", failed.code());
        assertEquals(fallbackSet(), repository.value.cards());
        assertThrows(ApiException.class, () -> new CardService(repository, (i, l, a) -> cards(QUESTIONS)).refresh("group", "stranger"));
    }

    private static List<GeneratedCard> cards(List<String> texts) {
        return texts.stream().map(text -> new GeneratedCard(text, "generic")).toList();
    }

    private static CardSet fallbackSet() {
        return new CardSet("en", CardSource.FALLBACK, List.of(new Card("c1", "Fallback one?", "generic"),
                new Card("c2", "Fallback two?", "generic"), new Card("c3", "Fallback three?", "generic"),
                new Card("c4", "Fallback four?", "generic"), new Card("c5", "Fallback five?", "generic")));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T12:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    private record FakeParticipants(Map<String, List<String>> interests) implements ParticipantRepository {
        public Optional<Participant> findById(String id) {
            return Optional.ofNullable(interests.get(id)).map(list -> new Participant(id, "hash-" + id, id, Category.DOMESTIC,
                    Campus.WUERZBURG, List.of("en"), list, List.of("2026-11-03T12:15"), null, MatchStatus.MATCHED, "group",
                    null, null, Instant.EPOCH, Instant.EPOCH, null));
        }
        public Optional<Participant> findByTokenHash(String h) { return Optional.empty(); }
        public List<Participant> findAll() { return List.of(); }
        public void save(Participant p) {}
        public void delete(String id) {}
    }

    private static final class FakeGroups implements GroupRepository {
        private LunchGroup value;
        private int saves;

        FakeGroups(CardSet cards) {
            value = new LunchGroup("group", Campus.WUERZBURG, "2026-11-03T12:15", "Mensa",
                    List.of("member", "other", "third", "fourth"), List.of("travel"), List.of("en"),
                    List.of("Everyone speaks English"), 1, Instant.EPOCH, cards);
        }

        public synchronized Optional<LunchGroup> findById(String id) {
            return value.id().equals(id) ? Optional.of(value) : Optional.empty();
        }
        public synchronized List<LunchGroup> findAll() { return List.of(value); }
        public synchronized void save(LunchGroup group) { value = group; saves++; }
    }
}
