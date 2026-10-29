package de.thws.lunch2.matching;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.ReasonCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {
    private static final String SLOT = "2026-11-03T12:15";
    private final MatchingEngine engine = new MatchingEngine();

    @Test
    void happyPathIsValidAndDeterministicAcrossThreeRuns() {
        List<MatchCandidate> candidates = List.of(candidate("d2", Category.DOMESTIC, null),
                candidate("i2", Category.INTERNATIONAL, null), candidate("d1", Category.DOMESTIC, null),
                candidate("i1", Category.INTERNATIONAL, null));
        MatchInput input = new MatchInput(candidates, List.of(SLOT), Set.of());
        MatchOutput result = engine.match(input);
        assertEquals(1, result.groups().size());
        assertTrue(result.waiting().isEmpty());
        ProposedGroup group = result.groups().getFirst();
        assertEquals(List.of("d1", "d2", "i1", "i2"), group.memberIds());
        assertEquals(List.of("en"), group.commonLanguages());
        assertEquals(List.of("travel"), group.sharedInterests());
        assertEquals(13, group.score()); // Six shared-interest pairs, plus English.
        assertTrue(GroupValidator.violations(group, index(candidates)).isEmpty());
        assertEquals(result, engine.match(input));
        assertEquals(result, engine.match(input));
        assertEquals(result, engine.match(new MatchInput(candidates.reversed(), List.of(SLOT), Set.of())));
    }

    @Test
    void friendPairIsPlacedTogetherEvenWithOtherChoices() {
        List<MatchCandidate> candidates = List.of(candidate("d1", Category.DOMESTIC, "d3"),
                candidate("d2", Category.DOMESTIC, null), candidate("d3", Category.DOMESTIC, "d1"),
                candidate("i1", Category.INTERNATIONAL, null), candidate("i2", Category.INTERNATIONAL, null));
        MatchOutput output = engine.match(new MatchInput(candidates, List.of(SLOT), Set.of()));
        assertEquals(List.of("d1", "d3", "i1", "i2"), output.groups().getFirst().memberIds());
        assertEquals(23, output.groups().getFirst().score());
        assertTrue(output.groups().getFirst().whyThisGroup().contains("FRIENDS_TOGETHER"));
        output.groups().forEach(g -> assertTrue(GroupValidator.violations(g, index(candidates)).isEmpty()));
    }

    @Test
    void validatorRejectsThreeDomesticAndOneInternational() {
        List<MatchCandidate> candidates = List.of(candidate("a", Category.DOMESTIC, null),
                candidate("b", Category.DOMESTIC, null), candidate("c", Category.DOMESTIC, null),
                candidate("d", Category.INTERNATIONAL, null));
        ProposedGroup group = new ProposedGroup(Campus.WUERZBURG, SLOT, List.of("a", "b", "c", "d"),
                List.of(), List.of("en"), List.of(), 0);
        assertEquals(List.of("CATEGORY_BALANCE"), GroupValidator.violations(group, index(candidates)));
    }

    @Test
    void equalScoresUseLexicographicallySmallestIds() {
        List<MatchCandidate> candidates = List.of(candidate("d3", Category.DOMESTIC, null),
                candidate("d2", Category.DOMESTIC, null), candidate("d1", Category.DOMESTIC, null),
                candidate("i2", Category.INTERNATIONAL, null), candidate("i1", Category.INTERNATIONAL, null));
        MatchOutput output = engine.match(new MatchInput(candidates, List.of(SLOT), Set.of()));
        assertEquals(List.of("d1", "d2", "i1", "i2"), output.groups().getFirst().memberIds());
        assertEquals(Map.of("d3", ReasonCode.NOT_ENOUGH_DOMESTIC), output.waiting());
    }

    @Test
    void validatorChecksRealLanguageIntersectionRatherThanDeclaredMetadata() {
        List<MatchCandidate> candidates = List.of(candidate("d1", Category.DOMESTIC, null),
                candidate("d2", Category.DOMESTIC, null), candidate("i1", Category.INTERNATIONAL, null),
                new MatchCandidate("i2", Category.INTERNATIONAL, Campus.WUERZBURG, Set.of("tr"),
                        Set.of(), Set.of(SLOT), null, null, Instant.EPOCH));
        ProposedGroup group = new ProposedGroup(Campus.WUERZBURG, SLOT, List.of("d1", "d2", "i1", "i2"),
                List.of(), List.of("en"), List.of(), 0);
        assertTrue(GroupValidator.violations(group, index(candidates)).contains("NO_COMMON_LANGUAGE"));
        MatchOutput output = engine.match(new MatchInput(candidates, List.of(SLOT), Set.of()));
        assertTrue(output.groups().isEmpty());
        assertTrue(output.waiting().values().stream().allMatch(r -> r == ReasonCode.NO_COMMON_LANGUAGE));
    }

    private static MatchCandidate candidate(String id, Category category, String friendId) {
        return new MatchCandidate(id, category, Campus.WUERZBURG, Set.of("en"), Set.of("travel"),
                Set.of(SLOT), null, friendId, Instant.EPOCH);
    }

    private static Map<String, MatchCandidate> index(List<MatchCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(MatchCandidate::id, Function.identity()));
    }
}
