package de.thws.lunch2.connect;

import de.thws.lunch2.auth.Authenticated;
import de.thws.lunch2.auth.CurrentParticipant;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/groups/{groupId}/connections")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class ConnectionResource {
    private final ConnectionService connections;
    private final CurrentParticipant currentParticipant;

    @Inject
    public ConnectionResource(ConnectionService connections, CurrentParticipant currentParticipant) {
        this.connections = connections;
        this.currentParticipant = currentParticipant;
    }

    @GET
    public List<ConnectionItem> list(@PathParam("groupId") String groupId) {
        return connections.list(groupId, currentParticipant.id());
    }

    @PUT
    @Path("/{participantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public ConnectionItem choose(@PathParam("groupId") String groupId, @PathParam("participantId") String participantId,
                                 @NotNull @Valid ConnectionChoiceRequest request) {
        return connections.choose(groupId, currentParticipant.id(), participantId, request.choice());
    }

    @PUT
    @Path("/{participantId}/contact")
    @Consumes(MediaType.APPLICATION_JSON)
    public ConnectionItem share(@PathParam("groupId") String groupId, @PathParam("participantId") String participantId,
                                @NotNull @Valid ContactRequest request) {
        return connections.shareContact(groupId, currentParticipant.id(), participantId, request.contact());
    }

    @DELETE
    @Path("/{participantId}/contact")
    public ConnectionItem withdraw(@PathParam("groupId") String groupId, @PathParam("participantId") String participantId) {
        return connections.withdrawContact(groupId, currentParticipant.id(), participantId);
    }
}
