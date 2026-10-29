package de.thws.lunch2.domain;

import java.time.Instant;
import java.util.List;

public record LunchGroup(
        String id,
        Campus campus,
        String slotId,
        String location,
        List<String> memberIds,
        List<String> sharedInterests,
        List<String> commonLanguages,
        List<String> whyThisGroup,
        int score,
        Instant createdAt,
        CardSet cards) {
    public LunchGroup {
        memberIds = List.copyOf(memberIds);
        sharedInterests = List.copyOf(sharedInterests);
        commonLanguages = List.copyOf(commonLanguages);
        whyThisGroup = List.copyOf(whyThisGroup);
    }
}
