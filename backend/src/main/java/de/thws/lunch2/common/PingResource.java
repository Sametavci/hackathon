package de.thws.lunch2.common;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** Bootstrap smoke endpoint: proves the server and JSON serialisation work. */
@Path("/api/ping")
@Produces(MediaType.APPLICATION_JSON)
public class PingResource {

    @GET
    public Map<String, Boolean> ping() {
        return Map.of("ok", true);
    }
}
