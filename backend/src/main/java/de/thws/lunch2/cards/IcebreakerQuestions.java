package de.thws.lunch2.cards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Shared with frontend mocks and the prototype game; no generated or personal data. */
public final class IcebreakerQuestions {
    private static final List<String> ENGLISH = load();
    private static final Map<String, List<String>> TRANSLATIONS = loadTranslations();

    private IcebreakerQuestions() {}

    public static List<String> english() {
        return ENGLISH;
    }

    public static List<String> forLanguage(String language) {
        return language.equals("en") ? ENGLISH : TRANSLATIONS.getOrDefault(language, ENGLISH);
    }

    private static Map<String, List<String>> loadTranslations() {
        try (var input = IcebreakerQuestions.class.getResourceAsStream("/cards/generic-translations.json")) {
            if (input == null) throw new IllegalStateException("The translated question bank is missing");
            Map<String, List<String>> banks = new ObjectMapper().readValue(input, new TypeReference<>() {});
            banks.replaceAll((language, questions) -> List.copyOf(questions));
            return Map.copyOf(banks);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load translated questions", exception);
        }
    }

    private static List<String> load() {
        try (var input = IcebreakerQuestions.class.getResourceAsStream("/cards/icebreakers.en.json")) {
            if (input == null) {
                throw new IllegalStateException("The classic icebreaker question bank is missing");
            }
            return List.of(new ObjectMapper().readValue(input, String[].class));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load classic icebreaker questions", exception);
        }
    }
}
