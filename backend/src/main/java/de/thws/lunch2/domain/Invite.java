package de.thws.lunch2.domain;

import java.time.Instant;

public record Invite(String code, String inviterId, Instant createdAt, String usedById) {}
