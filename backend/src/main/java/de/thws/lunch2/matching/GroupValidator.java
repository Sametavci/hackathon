package de.thws.lunch2.matching;

import de.thws.lunch2.domain.Category;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GroupValidator {
    private GroupValidator() {}

    public static List<String> violations(ProposedGroup g, Map<String, MatchCandidate> byId) {
        List<String> errors = new ArrayList<>();
        if (g.memberIds().size() != 4) errors.add("GROUP_SIZE");
        Set<String> ids = new HashSet<>(g.memberIds());
        if (ids.size() != g.memberIds().size()) errors.add("DUPLICATE_MEMBER");
        List<MatchCandidate> members = new ArrayList<>();
        for (String id : g.memberIds()) {
            MatchCandidate member = byId.get(id);
            if (member == null) {
                errors.add("UNKNOWN_MEMBER");
                continue;
            }
            members.add(member);
            if (member.campus() != g.campus()) errors.add("CAMPUS_MISMATCH");
            if (!member.slotIds().contains(g.slotId())) errors.add("SLOT_MISMATCH");
            if (member.friendId() != null && !ids.contains(member.friendId())) errors.add("FRIEND_MISSING");
        }
        if (members.stream().filter(m -> m.category() == Category.DOMESTIC).count() != 2
                || members.stream().filter(m -> m.category() == Category.INTERNATIONAL).count() != 2) {
            errors.add("CATEGORY_BALANCE");
        }
        Set<String> languages = new HashSet<>();
        if (!members.isEmpty()) {
            languages.addAll(members.getFirst().languages());
            for (MatchCandidate member : members) languages.retainAll(member.languages());
        }
        if (languages.isEmpty()) errors.add("NO_COMMON_LANGUAGE");
        return List.copyOf(errors);
    }
}
