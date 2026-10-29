package de.thws.lunch2.invite;

import de.thws.lunch2.auth.Authenticated;
import de.thws.lunch2.auth.CurrentParticipant;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/me/invite")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class InviteResource {
    private final InviteService invites;
    private final CurrentParticipant currentParticipant;

    @Inject
    public InviteResource(InviteService invites, CurrentParticipant currentParticipant) {
        this.invites = invites;
        this.currentParticipant = currentParticipant;
    }

    @POST
    public Response create() {
        return Response.status(Response.Status.CREATED).entity(invites.create(currentParticipant.id())).build();
    }

}
