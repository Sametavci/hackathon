package de.thws.lunch2.auth;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/api/test/class-admin")
@Produces(MediaType.APPLICATION_JSON)
@AdminOnly
public class AdminTestResource {
    @GET
    public Map<String, Boolean> admin() {
        return Map.of("ok", true);
    }
}
