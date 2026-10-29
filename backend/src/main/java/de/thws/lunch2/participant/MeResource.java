package de.thws.lunch2.participant;

import de.thws.lunch2.auth.Authenticated;
import de.thws.lunch2.auth.CurrentParticipant;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/me")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {
    private final CurrentParticipant currentParticipant;
    private final MeService me;

    @Inject
    public MeResource(CurrentParticipant currentParticipant, MeService me) {
        this.currentParticipant = currentParticipant;
        this.me = me;
    }

    @GET
    public MeResponse get() {
        return me.get(currentParticipant.id());
    }

    @DELETE
    public Response delete() {
        me.delete(currentParticipant.id());
        return Response.noContent().build();
    }
}
