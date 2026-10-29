package de.thws.lunch2.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api/test/class-auth")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AuthenticatedTestResource {
    private final CurrentParticipant currentParticipant;

    @Inject
    public AuthenticatedTestResource(CurrentParticipant currentParticipant) {
        this.currentParticipant = currentParticipant;
    }

    @GET
    public Map<String, String> participant() {
        return Map.of("id", currentParticipant.id());
    }
}
