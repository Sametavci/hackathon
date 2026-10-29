package de.thws.lunch2.matching;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.ReasonCode;
import java.util.List;

public record MatchRunResponse(List<CreatedGroup> groupsCreated, List<WaitingParticipant> waiting) {
    public record CreatedGroup(String groupId, Campus campus, String slotId, List<String> memberNames, int score) {}
    public record WaitingParticipant(String participantId, String displayName, ReasonCode reasonCode) {}
}
