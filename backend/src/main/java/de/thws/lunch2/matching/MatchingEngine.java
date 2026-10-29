package de.thws.lunch2.matching;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.ReasonCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class MatchingEngine {
    public MatchOutput match(MatchInput in) {
        Map<String, MatchCandidate> byId = new HashMap<>();
        List<MatchCandidate> candidates = in.candidates().stream()
                .sorted(Comparator.comparing(MatchCandidate::id)).toList();
        for (MatchCandidate c : candidates) {
            if (byId.putIfAbsent(c.id(), c) != null) throw new IllegalArgumentException("Duplicate candidate ID");
        }
        Map<String, ReasonCode> waiting = new LinkedHashMap<>();
        for (MatchCandidate c : candidates) {
            MatchCandidate friend = byId.get(c.friendId());
            if (friend == null || !c.id().equals(friend.friendId())) continue;
            ReasonCode issue = friendIssue(c, friend);
            if (issue != null) {
                waiting.put(c.id(), issue);
                waiting.put(friend.id(), issue);
            }
        }
        Set<String> assigned = new HashSet<>();
        List<ProposedGroup> groups = new ArrayList<>();
        for (Campus campus : Campus.values()) {
            for (String slot : in.slotIdsChronological()) {
                List<MatchCandidate> available = candidates.stream()
                        .filter(c -> c.campus() == campus && c.slotIds().contains(slot))
                        .filter(c -> !assigned.contains(c.id()) && !waiting.containsKey(c.id()))
                        .sorted(Comparator.comparing(MatchCandidate::registeredAt).thenComparing(MatchCandidate::id))
                        .toList();
                List<MatchCandidate> domestic = category(available, Category.DOMESTIC);
                List<MatchCandidate> international = category(available, Category.INTERNATIONAL);
                while (true) {
                    ProposedGroup best = null;
                    for (int a = 0; a < domestic.size(); a++) {
                        for (int b = a + 1; b < domestic.size(); b++) {
                            for (int c = 0; c < international.size(); c++) {
                                for (int d = c + 1; d < international.size(); d++) {
                                    List<MatchCandidate> members = List.of(domestic.get(a), domestic.get(b),
                                            international.get(c), international.get(d));
                                    Set<String> ids = new HashSet<>();
                                    members.forEach(m -> ids.add(m.id()));
                                    if (members.stream().anyMatch(m -> m.friendId() != null && !ids.contains(m.friendId()))) continue;
                                    Set<String> languages = commonLanguages(members);
                                    if (languages.isEmpty()) continue;
                                    ProposedGroup proposal = propose(campus, slot, members, languages, in.previouslyMetPairs());
                                    if (best == null || proposal.score() > best.score()
                                            || (proposal.score() == best.score() && compareIds(proposal.memberIds(), best.memberIds()) < 0)) {
                                        best = proposal;
                                    }
                                }
                            }
                        }
                    }
                    if (best == null) break;
                    groups.add(best);
                    assigned.addAll(best.memberIds());
                    domestic.removeIf(m -> assigned.contains(m.id()));
                    international.removeIf(m -> assigned.contains(m.id()));
                }
            }
        }
        List<MatchCandidate> remaining = candidates.stream()
                .filter(c -> !assigned.contains(c.id()) && !waiting.containsKey(c.id())).toList();
        for (MatchCandidate c : remaining) waiting.put(c.id(), waitingReason(c, remaining));
        return new MatchOutput(List.copyOf(groups), Collections.unmodifiableMap(new LinkedHashMap<>(waiting)));
    }

    private static List<MatchCandidate> category(List<MatchCandidate> pool, Category category) {
        return new ArrayList<>(pool.stream().filter(c -> c.category() == category).limit(40).toList());
    }

    private static ReasonCode friendIssue(MatchCandidate a, MatchCandidate b) {
        if (a.campus() != b.campus()) return ReasonCode.FRIEND_DIFFERENT_CAMPUS;
        if (Collections.disjoint(a.slotIds(), b.slotIds())) return ReasonCode.FRIEND_NO_COMMON_SLOT;
        if (Collections.disjoint(a.languages(), b.languages())) return ReasonCode.FRIEND_NO_COMMON_LANGUAGE;
        return null;
    }

    private static Set<String> commonLanguages(List<MatchCandidate> members) {
        Set<String> common = new TreeSet<>(members.getFirst().languages());
        for (MatchCandidate m : members) common.retainAll(m.languages());
        return common;
    }

    private static ProposedGroup propose(Campus campus, String slot, List<MatchCandidate> members,
            Set<String> languages, Set<Set<String>> previouslyMet) {
        int score = 0;
        Set<Set<String>> friends = new HashSet<>();
        Map<String, Integer> interestCounts = new HashMap<>();
        Set<String> fields = new HashSet<>();
        for (int i = 0; i < members.size(); i++) {
            MatchCandidate a = members.get(i);
            if (a.friendId() != null) friends.add(Set.of(a.id(), a.friendId()));
            if (a.studyField() != null && !a.studyField().isBlank()) fields.add(a.studyField());
            a.interests().forEach(interest -> interestCounts.merge(interest, 1, Integer::sum));
            for (int j = i + 1; j < members.size(); j++) {
                MatchCandidate b = members.get(j);
                score += 2 * (int) a.interests().stream().filter(b.interests()::contains).count();
                if (previouslyMet.contains(Set.of(a.id(), b.id()))) score -= 8;
            }
        }
        score += 10 * friends.size();
        if (fields.size() >= 3) score++;
        if (languages.contains("en") || languages.contains("de")) score++;
        List<String> why = new ArrayList<>();
        if (!friends.isEmpty()) why.add("FRIENDS_TOGETHER");
        List<String> interests = interestCounts.entrySet().stream().filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
        if (!interests.isEmpty()) why.add("SHARED_INTERESTS");
        if (fields.size() >= 3) why.add("DIVERSE_STUDY_FIELDS");
        return new ProposedGroup(campus, slot, members.stream().map(MatchCandidate::id).sorted().toList(),
                interests, List.copyOf(languages), List.copyOf(why), score);
    }

    private static int compareIds(List<String> a, List<String> b) {
        for (int i = 0; i < a.size(); i++) {
            int comparison = a.get(i).compareTo(b.get(i));
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static ReasonCode waitingReason(MatchCandidate candidate, List<MatchCandidate> remaining) {
        List<MatchCandidate> peers = remaining.stream().filter(c -> c.campus() == candidate.campus()
                && !Collections.disjoint(c.slotIds(), candidate.slotIds())).toList();
        long domestic = peers.stream().filter(c -> c.category() == Category.DOMESTIC).count();
        long international = peers.stream().filter(c -> c.category() == Category.INTERNATIONAL).count();
        if (domestic < 2) return ReasonCode.NOT_ENOUGH_DOMESTIC;
        if (international < 2) return ReasonCode.NOT_ENOUGH_INTERNATIONAL;
        // Look for a language with enough speakers in a single shared slot, including this candidate.
        boolean languagePossible = candidate.slotIds().stream().anyMatch(slot -> candidate.languages().stream()
                .anyMatch(language -> peers.stream().filter(c -> c.slotIds().contains(slot) && c.languages().contains(language))
                        .filter(c -> c.category() == Category.DOMESTIC).count() >= 2
                        && peers.stream().filter(c -> c.slotIds().contains(slot) && c.languages().contains(language))
                        .filter(c -> c.category() == Category.INTERNATIONAL).count() >= 2));
        if (!languagePossible) return ReasonCode.NO_COMMON_LANGUAGE;
        return domestic < international ? ReasonCode.NOT_ENOUGH_DOMESTIC : ReasonCode.NOT_ENOUGH_INTERNATIONAL;
    }
}
