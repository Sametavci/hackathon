package de.thws.lunch2.participant;

import de.thws.lunch2.common.CatalogService;
import de.thws.lunch2.common.SlotService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/meta")
@Produces(MediaType.APPLICATION_JSON)
public class MetaResource {
    private final CatalogService catalogs;
    private final SlotService slots;

    @Inject
    public MetaResource(CatalogService catalogs, SlotService slots) {
        this.catalogs = catalogs;
        this.slots = slots;
    }

    @GET
    public MetaResponse meta() {
        return new MetaResponse(catalogs.campuses(), catalogs.languages(), catalogs.interests(),
                catalogs.studyFields(), slots.slots());
    }
}
