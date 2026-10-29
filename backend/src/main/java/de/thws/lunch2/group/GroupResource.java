package de.thws.lunch2.group;

import de.thws.lunch2.auth.Authenticated;
import de.thws.lunch2.auth.CurrentParticipant;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/groups")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class GroupResource {
    private final CurrentParticipant current;
    private final GroupService groups;

    @Inject
    public GroupResource(CurrentParticipant current, GroupService groups) {
        this.current = current;
        this.groups = groups;
    }

    @GET
    @Path("/{groupId}")
    public GroupResponse get(@PathParam("groupId") String groupId) {
        return groups.get(groupId, current.get());
    }
}
