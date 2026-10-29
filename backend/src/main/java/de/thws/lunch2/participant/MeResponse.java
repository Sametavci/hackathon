package de.thws.lunch2.participant;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.ReasonCode;

import java.util.List;

public record MeResponse(String id, String displayName, Category category, Campus campus,
                         List<String> languages, List<String> interests, List<String> slotIds, String studyField,
                         MatchStatus matchStatus, Reason waitingReason, String groupId, Friend friend,
                         PendingInvite pendingInvite, String mensaId) {
    public record Reason(ReasonCode code, String message) {}
    public record Friend(String displayName, PairStatus pairStatus, Reason issue) {}
    public record PendingInvite(String code, String url) {}
}
