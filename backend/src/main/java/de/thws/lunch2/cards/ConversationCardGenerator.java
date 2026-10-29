package de.thws.lunch2.cards;

import java.util.List;

public interface ConversationCardGenerator {
    /** A generated question and the interest ID it is about ({@code "generic"} if none). */
    record GeneratedCard(String text, String topic) {}

    /**
     * @param interestIds the table's interests, most shared first
     * @param avoid       questions the table has already seen and should not get again
     */
    List<GeneratedCard> generate(List<String> interestIds, String languageCode, List<String> avoid) throws Exception;

    /** False when no provider is configured, so callers can skip retries and refreshes. */
    default boolean available() {
        return true;
    }
}
