package de.thws.lunch2.cards;

import de.thws.lunch2.auth.Authenticated;
import de.thws.lunch2.auth.CurrentParticipant;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/groups/{groupId}/cards")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class CardsResource {
    private final CurrentParticipant current;
    private final CardService cards;

    @Inject
    public CardsResource(CurrentParticipant current, CardService cards) {
        this.current = current;
        this.cards = cards;
    }

    @GET
    public CardsResponse get(@PathParam("groupId") String groupId) {
        return cards.get(groupId, current.id());
    }
}
