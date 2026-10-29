package de.thws.lunch2.group;

import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class GroupService {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private final GroupRepository groups;
    private final ParticipantRepository participants;
    private final FriendPairRepository pairs;

    @Inject
    public GroupService(GroupRepository groups, ParticipantRepository participants, FriendPairRepository pairs) {
        this.groups = groups;
        this.participants = participants;
        this.pairs = pairs;
    }

    public GroupResponse get(String groupId, Participant caller) {
        var group = groups.findById(groupId)
                .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Group not found."));
        if (!group.memberIds().contains(caller.id())) {
            throw new ApiException(403, "FORBIDDEN", "Only members can view this group.");
        }
        String friendId = friendId(caller);
        List<GroupResponse.MemberResponse> members = group.memberIds().stream().map(id -> participants.findById(id)
                .map(member -> new GroupResponse.MemberResponse(member.id(), member.displayName(), id.equals(caller.id()),
                        !id.equals(caller.id()) && id.equals(friendId), member.interests()))
                .orElseGet(() -> new GroupResponse.MemberResponse(id, "Former participant", false, false, List.of())))
                .toList();
        // Render the saved slot even if the configured event week changes after the group was created.
        LocalDateTime start = LocalDateTime.parse(group.slotId());
        GroupResponse.SlotResponse slot = new GroupResponse.SlotResponse(group.slotId(), start.toLocalDate().toString(),
                start.format(TIME), start.plusMinutes(45).format(TIME), start.format(LABEL_DATE) + " · " + start.format(TIME));
        return new GroupResponse(group.id(), group.campus(), slot,
                start.atZone(BERLIN).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), group.location(), members,
                group.sharedInterests(), group.commonLanguages(), group.whyThisGroup());
    }

    private String friendId(Participant caller) {
        if (caller.friendPairId() == null) return null;
        return pairs.findById(caller.friendPairId()).filter(pair -> pair.status() == PairStatus.ACTIVE)
                .map(pair -> otherMember(pair, caller.id())).orElse(null);
    }

    private static String otherMember(FriendPair pair, String callerId) {
        if (pair.inviterId().equals(callerId)) return pair.inviteeId();
        if (pair.inviteeId().equals(callerId)) return pair.inviterId();
        return null;
    }
}
