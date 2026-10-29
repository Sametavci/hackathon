package de.thws.lunch2.admin;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import java.util.List;

public record SeedResponse(List<DemoParticipant> participants) {
    public record DemoParticipant(String id, String displayName, Campus campus, Category category, String restoreUrl) {}
}
