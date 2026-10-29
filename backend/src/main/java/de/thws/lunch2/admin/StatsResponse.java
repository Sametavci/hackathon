package de.thws.lunch2.admin;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import java.util.Map;

public record StatsResponse(Map<Category, Long> registrations, Map<Campus, Long> byCampus, long matched,
        long waiting, double matchRate, long groups, long friendPairs, long mutualConnections) {}
