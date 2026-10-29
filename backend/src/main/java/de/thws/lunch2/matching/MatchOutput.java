package de.thws.lunch2.matching;

import de.thws.lunch2.domain.ReasonCode;
import java.util.List;
import java.util.Map;

public record MatchOutput(List<ProposedGroup> groups, Map<String, ReasonCode> waiting) {}
