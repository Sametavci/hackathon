package de.thws.lunch2.cards;

import de.thws.lunch2.domain.Card;
import de.thws.lunch2.domain.CardSource;
import java.util.List;

public record CardsResponse(String groupId, String language, CardSource source, List<Card> cards) {}
