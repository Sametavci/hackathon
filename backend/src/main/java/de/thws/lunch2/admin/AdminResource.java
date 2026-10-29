package de.thws.lunch2.admin;

import de.thws.lunch2.auth.AdminOnly;
import de.thws.lunch2.matching.MatchRunResponse;
import de.thws.lunch2.matching.MatchService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
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
    private final StatsService stats;

    @Inject
    public AdminResource(MatchService matching, SeedService seeding, StatsService stats) {
        this.matching = matching;
        this.seeding = seeding;
        this.stats = stats;
    }

    @GET
    @Path("/stats")
    public StatsResponse stats() {
        return stats.get();
    }

    @POST
    @Path("/seed")
    public SeedResponse seed() {
        return seeding.seed();
    }

    @POST
    @Path("/match")
    public MatchRunResponse match(@QueryParam("fillWithDemo") @DefaultValue("false") boolean fillWithDemo) {
        return fillWithDemo ? matching.matchWithDemo() : matching.match();
    }

    @POST
    @Path("/reset")
    public Response reset() {
        matching.reset();
        return Response.noContent().build();
    }
}
