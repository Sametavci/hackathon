package de.thws.lunch2.invite;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.MatchStatus;
import java.util.List;

public record InvitePreview(String code, String inviterDisplayName, Campus campus, boolean valid,
                            String mensaId, List<String> slotIds, MatchStatus inviterMatchStatus) {}
