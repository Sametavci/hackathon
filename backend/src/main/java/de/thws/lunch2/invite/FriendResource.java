package de.thws.lunch2.invite;

import de.thws.lunch2.auth.Authenticated;
import de.thws.lunch2.auth.CurrentParticipant;
import de.thws.lunch2.participant.MeResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/me/friend")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class FriendResource {
    private final InviteService invites;
    private final CurrentParticipant currentParticipant;

    @Inject
    public FriendResource(InviteService invites, CurrentParticipant currentParticipant) {
        this.invites = invites;
        this.currentParticipant = currentParticipant;
    }

    @POST
    @Path("/unlink")
    public MeResponse unlink() {
        return invites.unlink(currentParticipant.id());
    }
}
