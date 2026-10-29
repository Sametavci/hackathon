package de.thws.lunch2.matching;

import java.util.List;
import java.util.Set;

public record MatchInput(List<MatchCandidate> candidates, List<String> slotIdsChronological,
        Set<Set<String>> previouslyMetPairs) {}
