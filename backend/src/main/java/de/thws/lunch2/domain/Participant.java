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
        Instant consentAt,
        String mensaId) {
    // Compatibility for existing callers and snapshots predating Mensa selection.
    public Participant(String id, String tokenHash, String displayName, Category category, Campus campus,
            List<String> languages, List<String> interests, List<String> slotIds, String studyField,
            MatchStatus matchStatus, String groupId, ReasonCode waitingReason, String friendPairId,
            Instant createdAt, Instant consentAt) {
        this(id, tokenHash, displayName, category, campus, languages, interests, slotIds, studyField,
                matchStatus, groupId, waitingReason, friendPairId, createdAt, consentAt, null);
    }
    public Participant withMatch(MatchStatus status, String group, ReasonCode reason) {
        return new Participant(id, tokenHash, displayName, category, campus, languages, interests, slotIds,
                studyField, status, group, reason, friendPairId, createdAt, consentAt, mensaId);
    }
    public Participant withFriendPair(String pairId) {
        return new Participant(id, tokenHash, displayName, category, campus, languages, interests, slotIds,
                studyField, matchStatus, groupId, waitingReason, pairId, createdAt, consentAt, mensaId);
    }
    public Participant {
        languages = List.copyOf(languages);
        interests = List.copyOf(interests);
        slotIds = List.copyOf(slotIds);
    }
}
