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
    private final SeedService seeding;

    @Inject
    public AdminResource(MatchService matching, SeedService seeding) {
        this.matching = matching;
        this.seeding = seeding;
    }

    @POST
    @Path("/seed")
    public SeedResponse seed() {
        return seeding.seed();
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
        return Response.noContent().build();
    }
}
