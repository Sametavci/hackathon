package de.thws.lunch2.invite;

import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.common.MensaService;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.Invite;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.participant.MeResponse;
import de.thws.lunch2.participant.MeService;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.InviteRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class InviteService {
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private final SecureRandom random = new SecureRandom();
    private final InviteRepository invites;
    private final ParticipantRepository participants;
    private final FriendPairRepository pairs;
    private final MeService me;
    private final MensaService mensas;
    private final String publicBaseUrl;

    @Inject
    public InviteService(InviteRepository invites, ParticipantRepository participants, FriendPairRepository pairs,
                         MeService me, MensaService mensas, @ConfigProperty(name = "lunch2.public-base-url") String publicBaseUrl) {
        this.invites = invites;
        this.participants = participants;
        this.pairs = pairs;
        this.me = me;
        this.mensas = mensas;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public synchronized InviteResponse create(String participantId) {
        Participant participant = requireParticipant(participantId);
        requireUnpaired(participant);
        Invite invite = invites.findUnusedByInviter(participantId).orElseGet(() -> {
            Invite created = new Invite(newCode(), participantId, Instant.now(), null);
            invites.save(created);
            Log.info("Friend invite created");
            return created;
        });
        return new InviteResponse(invite.code(), publicBaseUrl + "/#/join/" + invite.code());
    }

    public synchronized InvitePreview preview(String code) {
        Invite invite = requireInvite(code);
        Participant inviter = participants.findById(invite.inviterId()).orElseThrow(InviteService::invalidInvite);
        return new InvitePreview(invite.code(), inviter.displayName(), inviter.campus(), invite.usedById() == null,
                mensas.resolve(inviter.campus(), inviter.mensaId()).id(), inviter.slotIds(), inviter.matchStatus());
    }

    public synchronized Participant attachIfPresent(Participant participant, String inviteCode) {
        if (inviteCode == null) {
            return participant;
        }
        Invite invite = requireInvite(inviteCode);
        if (invite.usedById() != null) {
            throw new ApiException(409, "INVITE_USED", "This friend invite has already been used.");
        }
        Participant inviter = participants.findById(invite.inviterId()).orElseThrow(InviteService::invalidInvite);
        requireUnpaired(inviter);
        ReasonCode issue = compatibilityIssue(inviter, participant);
        FriendPair pair = new FriendPair(UUID.randomUUID().toString(), inviter.id(), participant.id(),
                issue == null ? PairStatus.ACTIVE : PairStatus.INCOMPATIBLE, issue);
        pairs.save(pair);
        participants.save(withPair(inviter, pair.id()));
        invites.save(new Invite(invite.code(), invite.inviterId(), invite.createdAt(), participant.id()));
        Log.info("Friend pair created");
        return withPair(participant, pair.id());
    }

    public synchronized MeResponse unlink(String participantId) {
        Participant participant = requireParticipant(participantId);
        if (participant.friendPairId() != null) {
            pairs.findById(participant.friendPairId()).ifPresent(pair -> {
                if (!pair.inviterId().equals(participantId) && !pair.inviteeId().equals(participantId)) {
                    throw new ApiException(403, "FORBIDDEN", "You are not a member of this friend pair.");
                }
                pairs.save(new FriendPair(pair.id(), pair.inviterId(), pair.inviteeId(), PairStatus.UNLINKED, null));
                for (String id : new String[]{pair.inviterId(), pair.inviteeId()}) {
                    participants.findById(id).filter(member -> pair.id().equals(member.friendPairId()))
                            .ifPresent(member -> participants.save(withPair(member, null)));
                }
                Log.info("Friend pair unlinked");
            });
        }
        return me.get(participantId);
    }

    private String newCode() {
        String code;
        do {
            StringBuilder generated = new StringBuilder(10);
            for (int index = 0; index < 10; index++) {
                generated.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = generated.toString();
        } while (invites.findByCode(code).isPresent());
        return code;
    }

    private Participant requireParticipant(String id) {
        return participants.findById(id).orElseThrow(() ->
                new ApiException(401, "UNAUTHORIZED", "A valid access token is required."));
    }

    private Invite requireInvite(String code) {
        return invites.findByCode(code).orElseThrow(InviteService::invalidInvite);
    }

    private void requireUnpaired(Participant participant) {
        if (participant.friendPairId() != null && pairs.findById(participant.friendPairId())
                .filter(pair -> pair.status() != PairStatus.UNLINKED).isPresent()) {
            throw new ApiException(409, "ALREADY_PAIRED", "You already have a friend pair. Unlink it to invite someone else.");
        }
    }

    private ReasonCode compatibilityIssue(Participant inviter, Participant invitee) {
        if (inviter.matchStatus() == MatchStatus.MATCHED) {
            return ReasonCode.INVITER_ALREADY_MATCHED;
        }
        if (inviter.campus() != invitee.campus()) {
            return ReasonCode.FRIEND_DIFFERENT_CAMPUS;
        }
        if (!mensas.resolve(inviter.campus(), inviter.mensaId()).id().equals(mensas.resolve(invitee.campus(), invitee.mensaId()).id())) {
            return ReasonCode.FRIEND_DIFFERENT_MENSA;
        }
        if (inviter.slotIds().stream().noneMatch(invitee.slotIds()::contains)) {
            return ReasonCode.FRIEND_NO_COMMON_SLOT;
        }
        if (inviter.languages().stream().noneMatch(invitee.languages()::contains)) {
            return ReasonCode.FRIEND_NO_COMMON_LANGUAGE;
        }
        return null;
    }

    private static Participant withPair(Participant participant, String pairId) {
        ReasonCode reason = participant.waitingReason();
        if (pairId == null && participant.matchStatus() == MatchStatus.WAITING && reason != null) {
            reason = switch (reason) {
                case FRIEND_DIFFERENT_CAMPUS, FRIEND_DIFFERENT_MENSA, FRIEND_NO_COMMON_SLOT, FRIEND_NO_COMMON_LANGUAGE, INVITER_ALREADY_MATCHED ->
                        ReasonCode.WAITING_FOR_NEXT_ROUND;
                default -> reason;
            };
        }
        return new Participant(participant.id(), participant.tokenHash(), participant.displayName(), participant.category(),
                participant.campus(), participant.languages(), participant.interests(), participant.slotIds(),
                participant.studyField(), participant.matchStatus(), participant.groupId(), reason, pairId,
                participant.createdAt(), participant.consentAt(), participant.mensaId());
    }

    private static ApiException invalidInvite() {
        return new ApiException(404, "INVITE_INVALID", "This friend invite is not available.");
    }
}
