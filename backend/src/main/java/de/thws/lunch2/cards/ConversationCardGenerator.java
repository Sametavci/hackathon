package de.thws.lunch2.cards;

import java.util.List;

public interface ConversationCardGenerator {
    List<String> generate(List<String> interestIds, String languageCode) throws Exception;
}
