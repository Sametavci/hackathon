package de.thws.lunch2.domain;

import java.time.Instant;
import java.util.List;

public record Participant(
        String id,
        String tokenHash,
        String displayName,
        Category category,
        Campus campus,
        List<String> languages,
        List<String> interests,
        List<String> slotIds,
        String studyField,
        MatchStatus matchStatus,
        String groupId,
        ReasonCode waitingReason,
        String friendPairId,
        Instant createdAt,
        Instant consentAt) {
    public Participant {
        languages = List.copyOf(languages);
        interests = List.copyOf(interests);
        slotIds = List.copyOf(slotIds);
    }
}
