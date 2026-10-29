package de.thws.lunch2.matching;

import de.thws.lunch2.domain.Campus;
import java.util.List;

public record ProposedGroup(Campus campus, String slotId, List<String> memberIds,
        List<String> sharedInterests, List<String> commonLanguages, List<String> whyThisGroup, int score) {}
