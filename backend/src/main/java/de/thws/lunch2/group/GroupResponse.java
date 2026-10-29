package de.thws.lunch2.group;

import de.thws.lunch2.domain.Campus;
import java.util.List;

public record GroupResponse(String id, Campus campus, SlotResponse slot, String startsAt, String location,
        List<MemberResponse> members, List<String> sharedInterests, List<String> commonLanguages,
        List<String> whyThisGroup) {
    public record SlotResponse(String id, String date, String start, String end, String label) {}
    public record MemberResponse(String id, String displayName, boolean isMe, boolean isMyFriend,
            List<String> interests) {}
}
