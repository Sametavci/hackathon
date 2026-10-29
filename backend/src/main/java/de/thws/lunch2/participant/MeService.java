package de.thws.lunch2.participant;

import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.common.MensaService;
import de.thws.lunch2.common.ReasonMessages;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.persistence.ConnectionRepository;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.InviteRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MeService {
    private final ParticipantRepository participants;
    private final FriendPairRepository pairs;
    private final InviteRepository invites;
    private final ConnectionRepository connections;
    private final GroupRepository groups;
    private final ReasonMessages reasons;
    private final MensaService mensas;
    private final String publicBaseUrl;

    @Inject
    public MeService(ParticipantRepository participants, FriendPairRepository pairs, InviteRepository invites,
                     ConnectionRepository connections, GroupRepository groups, ReasonMessages reasons, MensaService mensas,
                     @ConfigProperty(name = "lunch2.public-base-url") String publicBaseUrl) {
        this.participants = participants;
        this.pairs = pairs;
        this.invites = invites;
        this.connections = connections;
        this.groups = groups;
        this.reasons = reasons;
        this.mensas = mensas;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public MeResponse get(String participantId) {
        return toResponse(requireParticipant(participantId));
    }

    public MeResponse toResponse(Participant participant) {
        var pendingInvite = invites.findUnusedByInviter(participant.id())
                .map(invite -> new MeResponse.PendingInvite(invite.code(), publicBaseUrl + "/#/join/" + invite.code()))
                .orElse(null);
        return new MeResponse(participant.id(), participant.displayName(), participant.category(), participant.campus(),
                participant.languages(), participant.interests(), participant.slotIds(), participant.studyField(),
                participant.matchStatus(), participant.matchStatus() == MatchStatus.WAITING
                ? reason(participant.waitingReason()) : null,
                participant.matchStatus() == MatchStatus.MATCHED ? participant.groupId() : null,
                friend(participant), pendingInvite, mensas.resolve(participant.campus(), participant.mensaId()).id());
    }

    public void delete(String participantId) {
        Participant participant = requireParticipant(participantId);
        invites.deleteByInviter(participantId);
        connections.deleteByParticipant(participantId);
        // Include old UNLINKED pairs so no pair retains the erased participant's ID.
        for (FriendPair pair : pairs.findAll()) {
            if (pair.inviterId().equals(participantId) || pair.inviteeId().equals(participantId)) {
                String friendId = pair.inviterId().equals(participantId) ? pair.inviteeId() : pair.inviterId();
                participants.findById(friendId)
                        .filter(friend -> pair.id().equals(friend.friendPairId()))
                        .ifPresent(friend -> participants.save(withoutFriendPair(friend)));
                pairs.delete(pair.id());
            }
        }
        for (LunchGroup group : groups.findAll()) {
            if (group.memberIds().contains(participantId)) {
                // GroupService renders the missing member as a tombstone; retain the member ID.
                var explanations = group.whyThisGroup().stream()
                        .filter(text -> !text.contains(participant.displayName())).toList();
                if (!explanations.equals(group.whyThisGroup())) {
                    groups.save(new LunchGroup(group.id(), group.campus(), group.slotId(), group.location(),
                            group.memberIds(), group.sharedInterests(), group.commonLanguages(), explanations,
                            group.score(), group.createdAt(), group.cards()));
                }
            }
        }
        participants.delete(participantId);
        Log.info("Participant data deleted");
    }

    private Participant requireParticipant(String id) {
        return participants.findById(id).orElseThrow(() ->
                new ApiException(401, "UNAUTHORIZED", "A valid access token is required."));
    }

    private MeResponse.Friend friend(Participant participant) {
        if (participant.friendPairId() == null) {
            return null;
        }
        return pairs.findById(participant.friendPairId())
                .filter(pair -> pair.status() != PairStatus.UNLINKED)
                .filter(pair -> pair.inviterId().equals(participant.id()) || pair.inviteeId().equals(participant.id()))
                .flatMap(pair -> {
                    String friendId = pair.inviterId().equals(participant.id()) ? pair.inviteeId() : pair.inviterId();
                    return participants.findById(friendId).map(friend ->
                            new MeResponse.Friend(friend.displayName(), pair.status(), reason(pair.issue())));
                }).orElse(null);
    }

    private MeResponse.Reason reason(ReasonCode code) {
        return code == null ? null : new MeResponse.Reason(code, reasons.message(code));
    }

    private static Participant withoutFriendPair(Participant friend) {
        ReasonCode waitingReason = friend.waitingReason();
        if (friend.matchStatus() == MatchStatus.WAITING && waitingReason != null) {
            waitingReason = switch (waitingReason) {
                case FRIEND_DIFFERENT_CAMPUS, FRIEND_DIFFERENT_MENSA, FRIEND_NO_COMMON_SLOT, FRIEND_NO_COMMON_LANGUAGE, INVITER_ALREADY_MATCHED ->
                        ReasonCode.WAITING_FOR_NEXT_ROUND;
                default -> waitingReason;
            };
        }
        return new Participant(friend.id(), friend.tokenHash(), friend.displayName(), friend.category(), friend.campus(),
                friend.languages(), friend.interests(), friend.slotIds(), friend.studyField(), friend.matchStatus(),
                friend.groupId(), waitingReason, null, friend.createdAt(), friend.consentAt(), friend.mensaId());
    }
}
