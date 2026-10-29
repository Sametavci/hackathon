package de.thws.lunch2.matching;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import java.time.Instant;
import java.util.Set;

public record MatchCandidate(String id, Category category, Campus campus, Set<String> languages,
        Set<String> interests, Set<String> slotIds, String studyField, String friendId, Instant registeredAt) {}
