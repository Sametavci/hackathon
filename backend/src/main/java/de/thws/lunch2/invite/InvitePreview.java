package de.thws.lunch2.invite;

import de.thws.lunch2.domain.Campus;

public record InvitePreview(String code, String inviterDisplayName, Campus campus, boolean valid) {}
