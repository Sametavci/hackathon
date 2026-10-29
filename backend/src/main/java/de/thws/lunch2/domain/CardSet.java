package de.thws.lunch2.domain;

import java.util.List;

public record CardSet(String language, CardSource source, List<Card> cards) {
    public CardSet {
        cards = List.copyOf(cards);
    }
}
