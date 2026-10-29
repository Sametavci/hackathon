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
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardServiceTest {
    @Test
    void concurrentFirstRequestsPersistExactlyOneCardSet() throws Exception {
        FakeGroups repository = new FakeGroups(null);
        CardService service = new CardService(repository);
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
        }
        // Cache survives a fresh service instance, since it belongs to the repository's saved group.
        CardsResponse restored = new CardService(repository).get("group", "member");
        assertEquals(repository.value.cards().cards(), restored.cards());
        assertEquals(1, repository.saves);
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
