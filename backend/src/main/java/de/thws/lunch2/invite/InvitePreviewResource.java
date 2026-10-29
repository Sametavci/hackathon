package de.thws.lunch2.invite;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/invites")
@Produces(MediaType.APPLICATION_JSON)
public class InvitePreviewResource {
    private final InviteService invites;

    @Inject
    public InvitePreviewResource(InviteService invites) {
        this.invites = invites;
    }

    @GET
    @Path("/{code}")
    public InvitePreview preview(@PathParam("code") String code) {
        return invites.preview(code);
    }
}
