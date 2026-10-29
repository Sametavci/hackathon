package de.thws.lunch2.cards;

import de.thws.lunch2.domain.Card;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FallbackCardsTest {
    private static final List<String> INTERESTS = List.of("gaming", "travel", "tech", "football", "music", "cooking",
            "hiking", "movies", "photography", "art", "reading", "fitness", "startups", "languages", "anime", "boardgames");

    @Test
    void eachInterestHasTwoQuestionsInEnglishAndGerman() {
        for (String lang : List.of("en", "de")) {
            for (String interest : INTERESTS) {
                List<Card> cards = FallbackCards.pick("example-group", List.of(interest), lang);
                assertValid(cards);
                assertEquals(2, cards.stream().filter(card -> card.topic().equals(interest)).count());
                assertEquals(3, cards.stream().filter(card -> card.topic().equals("generic")).count());
            }
        }
    }

    @Test
    void englishAndGermanEachHaveEightGenericQuestions() {
        for (String lang : List.of("en", "de")) {
            Set<String> questions = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                List<Card> cards = FallbackCards.pick("group-" + i, List.of(), lang);
                assertValid(cards);
                cards.forEach(card -> questions.add(card.text()));
            }
            assertEquals(8, questions.size());
        }
    }

    @Test
    void allOtherCatalogLanguagesHaveFiveDistinctGenericCards() {
        for (String lang : List.of("tr", "ar", "es", "fr", "it", "pt", "ru", "uk", "zh", "hi")) {
            List<Card> cards = FallbackCards.pick("group", INTERESTS, lang);
            assertValid(cards);
            assertTrue(cards.stream().allMatch(card -> card.topic().equals("generic")));
            assertNotEquals(FallbackCards.pick("group", List.of(), "en"), cards);
        }
    }

    @Test
    void selectionIsDeterministicIndependentOfInterestOrderAndDuplicates() {
        List<Card> expected = FallbackCards.pick("fixed-id", List.of("travel", "gaming"), "en");
        for (int run = 0; run < 3; run++) {
            assertEquals(expected, FallbackCards.pick("fixed-id", List.of("gaming", "travel", "gaming"), "en"));
        }
        assertNotEquals(expected, FallbackCards.pick("another-id", List.of("travel", "gaming"), "en"));
        assertNotEquals(expected, FallbackCards.pick("fixed-id", List.of("travel", "gaming"), "de"));
    }

    @Test
    void inputsAreNotMutatedAndResultIsImmutable() {
        List<String> interests = new ArrayList<>(List.of("travel", "gaming"));
        List<Card> cards = FallbackCards.pick("group", interests, "en");
        assertEquals(List.of("travel", "gaming"), interests);
        assertThrows(UnsupportedOperationException.class, () -> cards.clear());
    }

    @Test
    void unknownInterestsStillProduceFiveSafeGenericCards() {
        List<Card> cards = FallbackCards.pick("polygenelubricants", List.of("unknown"), "en");
        assertValid(cards);
        assertTrue(cards.stream().allMatch(card -> card.topic().equals("generic")));
    }

    private static void assertValid(List<Card> cards) {
        assertEquals(5, cards.size());
        assertEquals(List.of("c1", "c2", "c3", "c4", "c5"), cards.stream().map(Card::id).toList());
        assertEquals(5, cards.stream().map(Card::text).distinct().count());
        for (Card card : cards) {
            assertFalse(card.text().isBlank());
            assertTrue(card.text().length() <= 140, card.text());
            assertFalse(card.topic().isBlank());
        }
    }
}
