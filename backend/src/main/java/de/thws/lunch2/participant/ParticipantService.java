package de.thws.lunch2.participant;

import de.thws.lunch2.auth.TokenService;
import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.common.CatalogService;
import de.thws.lunch2.common.ErrorResponse;
import de.thws.lunch2.common.SlotService;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.invite.InviteService;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ParticipantService {
    private final ParticipantRepository participants;
    private final TokenService tokens;
    private final CatalogService catalogs;
    private final SlotService slots;
    private final InviteService invites;
    private final String publicBaseUrl;

    @Inject
    public ParticipantService(ParticipantRepository participants, TokenService tokens, CatalogService catalogs,
                              SlotService slots, InviteService invites,
                              @ConfigProperty(name = "lunch2.public-base-url") String publicBaseUrl) {
        this.participants = participants;
        this.tokens = tokens;
        this.catalogs = catalogs;
        this.slots = slots;
        this.invites = invites;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public RegistrationResponse register(RegistrationRequest request) {
        if (!Boolean.TRUE.equals(request.consent())) {
            throw new ApiException(400, "CONSENT_REQUIRED", "Please consent to the use of your data to join lunch.");
        }
        if (request.languages().stream().anyMatch(code -> !catalogs.hasLanguage(code))) {
            throw invalidField("languages", "Please choose known language codes.");
        }
        if (request.interests().stream().anyMatch(id -> !catalogs.hasInterest(id))) {
            throw invalidField("interests", "Please choose known interest IDs.");
        }
        if (request.slotIds().stream().anyMatch(id -> slots.findById(id).isEmpty())) {
            throw new ApiException(400, "UNKNOWN_SLOT", "Please choose an available lunch slot.",
                    List.of(new ErrorResponse.FieldError("slotIds", "Unknown lunch slot.")));
        }
        if (request.studyField() != null && !catalogs.hasStudyField(request.studyField())) {
            throw invalidField("studyField", "Please choose a known study field.");
        }
        String token = tokens.newToken();
        Instant now = Instant.now();
        Participant participant = new Participant(UUID.randomUUID().toString(), tokens.hash(token), request.displayName(),
                request.category(), request.campus(), request.languages(), request.interests(), request.slotIds(),
                request.studyField(), MatchStatus.WAITING, null, ReasonCode.WAITING_FOR_NEXT_ROUND, null, now, now);
        participant = invites.attachIfPresent(participant, request.inviteCode());
        participants.save(participant);
        Log.info("Participant registered");
        MeResponse me = new MeResponse(participant.id(), participant.displayName(), participant.category(),
                participant.campus(), participant.languages(), participant.interests(), participant.slotIds(),
                participant.studyField(), participant.matchStatus(), new MeResponse.Reason(participant.waitingReason(),
                "Matching has not run since you registered."), null, null, null);
        return new RegistrationResponse(token, publicBaseUrl + "/#/back?t=" + token, me);
    }

    private static ApiException invalidField(String field, String message) {
        return new ApiException(400, "VALIDATION_FAILED", message, List.of(new ErrorResponse.FieldError(field, message)));
    }
}
