package de.thws.lunch2.domain;

public record ConnectionChoice(
        String groupId, String fromId, String toId, ConnectChoice choice, String contact) {}
