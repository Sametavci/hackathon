package de.thws.lunch2.cards;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Card;
import de.thws.lunch2.domain.CardSet;
import de.thws.lunch2.domain.CardSource;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.persistence.GroupRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardServiceTest {
    private static final List<String> QUESTIONS = List.of("Which game would you suggest?", "What would you like to explore?",
            "What music fits this lunch?", "Which skill would you try?", "What makes a good study break?");
    @Test
    void concurrentFirstRequestsPersistExactlyOneCardSet() throws Exception {
        FakeGroups repository = new FakeGroups(null);
        AtomicInteger callsMade = new AtomicInteger();
        CardService service = new CardService(repository, (interests, language) -> {
            callsMade.incrementAndGet();
            return QUESTIONS;
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
        CardService service = new CardService(repository, (interests, language) -> {
            assertEquals(List.of("travel"), interests);
            assertEquals("en", language);
            return QUESTIONS;
        });
        assertEquals(CardSource.AI, service.get("group", "member").source());
        assertEquals(QUESTIONS, repository.value.cards().cards().stream().map(Card::text).toList());
    }

    @Test
    void throwingGeneratorFallsBackAndFailureIsNotRetriedAfterCaching() {
        FakeGroups repository = new FakeGroups(null);
        AtomicInteger attempts = new AtomicInteger();
        CardService service = new CardService(repository, (interests, language) -> {
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
            assertEquals(CardSource.FALLBACK, new CardService(repository, (interests, lang) -> invalid)
                    .get("group", "member").source());
        }
    }

    @Test
    void threeFourOrSixValidQuestionsAreNormalizedToExactlyFiveCards() {
        for (int count : List.of(3, 4, 6)) {
            List<String> generated = java.util.stream.IntStream.range(0, count).mapToObj(i -> "Question " + i + "?").toList();
            CardsResponse result = new CardService(new FakeGroups(null), (interests, lang) -> generated).get("group", "member");
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
