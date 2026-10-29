package de.thws.lunch2.admin;

import de.thws.lunch2.auth.AdminOnly;
import de.thws.lunch2.matching.MatchRunResponse;
import de.thws.lunch2.matching.MatchService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin")
@AdminOnly
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {
    private final MatchService matching;

    @Inject
    public AdminResource(MatchService matching) {
        this.matching = matching;
    }

    @POST
    @Path("/match")
    public MatchRunResponse match() {
        return matching.match();
    }

    @POST
    @Path("/reset")
    public Response reset() {
        matching.reset();
        // Provisional A3 response agreed with the user; recheck the contract in the next ticket.
        return Response.noContent().build();
    }
}
