package de.thws.lunch2.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** Test-only endpoints for method bindings and request context isolation. */
@Path("/api/test/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthTestResource {
    private final CurrentParticipant currentParticipant;

    @Inject
    public AuthTestResource(CurrentParticipant currentParticipant) {
        this.currentParticipant = currentParticipant;
    }

    @GET
    @Path("/participant")
    @Authenticated
    public Map<String, String> participant() {
        return Map.of("id", currentParticipant.id(), "participantId", currentParticipant.get().id());
    }

    @GET
    @Path("/admin")
    @AdminOnly
    public Map<String, Boolean> admin() {
        return Map.of("ok", true);
    }

    @GET
    @Path("/public")
    public Map<String, Boolean> publicEndpoint() {
        return Map.of("ok", true);
    }

    @GET
    @Path("/unpopulated-context")
    public Map<String, String> unpopulatedContext() {
        return Map.of("id", currentParticipant.id());
    }
}
