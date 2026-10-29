package de.thws.lunch2.matching;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.ReasonCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchingEdgeCasesTest {
    private static final String EARLY = "2026-11-03T11:30";
    private static final String LATE = "2026-11-03T12:15";
    private final MatchingEngine engine = new MatchingEngine();

    @Test
    void threeParticipantsWaitWithAnExplanation() {
        MatchOutput output = match(List.of(domestic("d1"), domestic("d2"), international("i1")));
        assertTrue(output.groups().isEmpty());
        assertEquals(Map.of("d1", ReasonCode.NOT_ENOUGH_INTERNATIONAL,
                "d2", ReasonCode.NOT_ENOUGH_INTERNATIONAL, "i1", ReasonCode.NOT_ENOUGH_INTERNATIONAL), output.waiting());
    }

    @Test
    void fourDomesticParticipantsWaitForInternationalParticipants() {
        MatchOutput output = match(List.of(domestic("d1"), domestic("d2"), domestic("d3"), domestic("d4")));
        assertTrue(output.groups().isEmpty());
        assertEquals(4, output.waiting().size());
        assertTrue(output.waiting().values().stream().allMatch(r -> r == ReasonCode.NOT_ENOUGH_INTERNATIONAL));
    }

    @Test
    void fourInternationalParticipantsWaitForDomesticParticipants() {
        MatchOutput output = match(List.of(international("i1"), international("i2"),
                international("i3"), international("i4")));
        assertTrue(output.groups().isEmpty());
        assertEquals(4, output.waiting().size());
        assertTrue(output.waiting().values().stream().allMatch(r -> r == ReasonCode.NOT_ENOUGH_DOMESTIC));
    }

    @Test
    void sixDomesticAndTwoInternationalProduceExactlyOneGroup() {
        MatchOutput output = match(List.of(domestic("d1"), domestic("d2"), domestic("d3"),
                domestic("d4"), domestic("d5"), domestic("d6"), international("i1"), international("i2")));
        assertEquals(1, output.groups().size());
        assertEquals(List.of("d1", "d2", "i1", "i2"), output.groups().getFirst().memberIds());
        assertEquals(4, output.waiting().size());
        assertTrue(output.waiting().values().stream().allMatch(r -> r == ReasonCode.NOT_ENOUGH_INTERNATIONAL));
    }

    @Test
    void incompatibleSlotsCannotProduceAGroup() {
        MatchOutput output = match(List.of(domestic("d1"), domestic("d2"),
                candidate("i1", Category.INTERNATIONAL, Campus.WUERZBURG, Set.of(LATE), Set.of("en"), null),
                candidate("i2", Category.INTERNATIONAL, Campus.WUERZBURG, Set.of(LATE), Set.of("en"), null)));
        assertTrue(output.groups().isEmpty());
        assertEquals(Map.of("d1", ReasonCode.NOT_ENOUGH_INTERNATIONAL, "d2", ReasonCode.NOT_ENOUGH_INTERNATIONAL,
                "i1", ReasonCode.NOT_ENOUGH_DOMESTIC, "i2", ReasonCode.NOT_ENOUGH_DOMESTIC), output.waiting());
    }

    @Test
    void pairwiseLanguageOverlapIsInsufficientWithoutOneLanguageForAllFour() {
        MatchOutput output = match(List.of(
                candidate("d1", Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("en", "de"), null),
                candidate("d2", Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("en", "fr"), null),
                candidate("i1", Category.INTERNATIONAL, Campus.WUERZBURG, Set.of(EARLY), Set.of("de", "fr"), null),
                candidate("i2", Category.INTERNATIONAL, Campus.WUERZBURG, Set.of(EARLY), Set.of("en", "de", "fr"), null)));
        assertTrue(output.groups().isEmpty());
        assertEquals(4, output.waiting().size());
        assertTrue(output.waiting().values().stream().allMatch(r -> r == ReasonCode.NO_COMMON_LANGUAGE));
    }

    @Test
    void crossCampusFriendPairWaitsEvenWhenEachCampusHasEnoughParticipants() {
        List<MatchCandidate> candidates = new ArrayList<>();
        candidates.add(candidate("friendW", Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("en"), "friendS"));
        candidates.add(candidate("friendS", Category.DOMESTIC, Campus.SCHWEINFURT, Set.of(EARLY), Set.of("en"), "friendW"));
        for (Campus campus : Campus.values()) {
            for (int n = 1; n <= 2; n++) {
                candidates.add(candidate(campus.name() + "d" + n, Category.DOMESTIC, campus, Set.of(EARLY), Set.of("en"), null));
                candidates.add(candidate(campus.name() + "i" + n, Category.INTERNATIONAL, campus, Set.of(EARLY), Set.of("en"), null));
            }
        }
        MatchOutput output = match(candidates);
        assertEquals(2, output.groups().size());
        assertEquals(Map.of("friendW", ReasonCode.FRIEND_DIFFERENT_CAMPUS,
                "friendS", ReasonCode.FRIEND_DIFFERENT_CAMPUS), output.waiting());
        assertEquals(List.of(Campus.WUERZBURG, Campus.SCHWEINFURT),
                output.groups().stream().map(ProposedGroup::campus).toList());
    }

    @Test
    void friendsWithoutACommonSlotReceiveTheFriendIssueBeforeScarcity() {
        MatchOutput output = match(List.of(
                candidate("a", Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("en"), "b"),
                candidate("b", Category.DOMESTIC, Campus.WUERZBURG, Set.of(LATE), Set.of("en"), "a")));
        assertTrue(output.groups().isEmpty());
        assertEquals(Map.of("a", ReasonCode.FRIEND_NO_COMMON_SLOT, "b", ReasonCode.FRIEND_NO_COMMON_SLOT), output.waiting());
    }

    @Test
    void friendsWithoutACommonLanguageReceiveTheSameIssue() {
        MatchOutput output = match(List.of(
                candidate("a", Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("en"), "b"),
                candidate("b", Category.INTERNATIONAL, Campus.WUERZBURG, Set.of(EARLY), Set.of("de"), "a")));
        assertTrue(output.groups().isEmpty());
        assertEquals(Map.of("a", ReasonCode.FRIEND_NO_COMMON_LANGUAGE, "b", ReasonCode.FRIEND_NO_COMMON_LANGUAGE), output.waiting());
    }

    @Test
    void friendPairIsNeverSplitEvenIfSplittingWouldAllowAGroup() {
        MatchCandidate a = candidate("d1", Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("en", "de"), "d2");
        MatchCandidate b = candidate("d2", Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("en", "fr"), "d1");
        MatchCandidate other = candidate("d3", Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("de"), null);
        MatchCandidate i1 = candidate("i1", Category.INTERNATIONAL, Campus.WUERZBURG, Set.of(EARLY), Set.of("de"), null);
        MatchCandidate i2 = candidate("i2", Category.INTERNATIONAL, Campus.WUERZBURG, Set.of(EARLY), Set.of("de"), null);
        MatchOutput together = match(List.of(a, b, other, i1, i2));
        assertTrue(together.groups().isEmpty());
        assertEquals(5, together.waiting().size());
        MatchCandidate unlinked = candidate(a.id(), a.category(), a.campus(), a.slotIds(), a.languages(), null);
        MatchOutput separate = match(List.of(unlinked, other, i1, i2));
        assertEquals(1, separate.groups().size(), "A valid group exists only after explicitly unlinking the pair");
    }

    @Test
    void noParticipantIsAssignedTwiceAcrossSlots() {
        Set<String> both = Set.of(EARLY, LATE);
        List<MatchCandidate> candidates = new ArrayList<>();
        for (int n = 1; n <= 4; n++) {
            Set<String> slots = n <= 2 ? both : Set.of(LATE);
            candidates.add(candidate("d" + n, Category.DOMESTIC, Campus.WUERZBURG, slots, Set.of("en"), null));
            candidates.add(candidate("i" + n, Category.INTERNATIONAL, Campus.WUERZBURG, slots, Set.of("en"), null));
        }
        MatchOutput output = match(candidates);
        assertEquals(2, output.groups().size());
        assertEquals(List.of(EARLY, LATE), output.groups().stream().map(ProposedGroup::slotId).toList());
        assertTrue(output.waiting().isEmpty());
    }

    @Test
    void previouslyMetPenaltyChangesTheSelectedGroup() {
        List<MatchCandidate> candidates = List.of(domestic("d1"), domestic("d2"), domestic("d3"),
                international("i1"), international("i2"));
        MatchOutput fresh = match(candidates);
        MatchOutput repeat = match(candidates, Set.of(Set.of("d1", "i1")));
        assertEquals(List.of("d1", "d2", "i1", "i2"), fresh.groups().getFirst().memberIds());
        assertEquals(List.of("d2", "d3", "i1", "i2"), repeat.groups().getFirst().memberIds());
        assertEquals(13, repeat.groups().getFirst().score());
    }

    @Test
    void repeatMeetingsAreASoftPenaltyRatherThanAHardExclusion() {
        MatchOutput output = match(List.of(domestic("d1"), domestic("d2"), international("i1"), international("i2")),
                Set.of(Set.of("d1", "i1")));
        assertEquals(1, output.groups().size());
        assertEquals(5, output.groups().getFirst().score());
    }

    @Test
    void shufflingCandidateOrderDoesNotChangeGroupsScoresOrReasons() {
        List<MatchCandidate> candidates = new ArrayList<>(List.of(domestic("d1"), domestic("d2"), domestic("d3"),
                domestic("d4"), domestic("d5"), international("i1"), international("i2"),
                international("i3"), international("i4")));
        MatchOutput expected = match(candidates);
        Random random = new Random(202);
        for (int run = 0; run < 3; run++) {
            Collections.shuffle(candidates, random);
            assertEquals(expected, match(candidates));
        }
    }

    @Test
    void categoryCapUsesRegistrationTimeBeforeLexicographicIds() {
        List<MatchCandidate> candidates = new ArrayList<>();
        // The lexicographically first candidate arrived last and must be outside the 40-person pool.
        candidates.add(new MatchCandidate("a-new", Category.DOMESTIC, Campus.WUERZBURG, Set.of("en"),
                Set.of("travel"), Set.of(EARLY), null, null, Instant.EPOCH.plusSeconds(1)));
        for (int n = 1; n <= 40; n++) candidates.add(domestic("d" + n));
        candidates.add(international("i1"));
        candidates.add(international("i2"));
        MatchOutput output = match(candidates);
        assertEquals(List.of("d1", "d10", "i1", "i2"), output.groups().getFirst().memberIds());
        assertTrue(output.waiting().containsKey("a-new"));
    }

    private MatchOutput match(List<MatchCandidate> candidates) {
        return match(candidates, Set.of());
    }

    private MatchOutput match(List<MatchCandidate> candidates, Set<Set<String>> previouslyMet) {
        MatchOutput output = engine.match(new MatchInput(candidates, List.of(EARLY, LATE), previouslyMet));
        Map<String, MatchCandidate> byId = candidates.stream()
                .collect(Collectors.toMap(MatchCandidate::id, Function.identity()));
        Set<String> assigned = new HashSet<>();
        for (ProposedGroup group : output.groups()) {
            assertEquals(List.of(), GroupValidator.violations(group, byId));
            for (String id : group.memberIds()) {
                assertTrue(assigned.add(id), "Participant assigned more than once: " + id);
                assertFalse(output.waiting().containsKey(id), "Assigned participant is also waiting: " + id);
            }
        }
        Set<String> accountedFor = new HashSet<>(assigned);
        accountedFor.addAll(output.waiting().keySet());
        assertEquals(byId.keySet(), accountedFor, "Every participant is assigned or waiting");
        assertTrue(output.waiting().values().stream().allMatch(reason -> reason != null));
        return output;
    }

    private static MatchCandidate domestic(String id) {
        return candidate(id, Category.DOMESTIC, Campus.WUERZBURG, Set.of(EARLY), Set.of("en"), null);
    }

    private static MatchCandidate international(String id) {
        return candidate(id, Category.INTERNATIONAL, Campus.WUERZBURG, Set.of(EARLY), Set.of("en"), null);
    }

    private static MatchCandidate candidate(String id, Category category, Campus campus, Set<String> slots,
            Set<String> languages, String friendId) {
        return new MatchCandidate(id, category, campus, languages, Set.of("travel"), slots, null, friendId, Instant.EPOCH);
    }
}
