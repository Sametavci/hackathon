package de.thws.lunch2.participant;

public record RegistrationResponse(String accessToken, String restoreUrl, MeResponse me) {}
