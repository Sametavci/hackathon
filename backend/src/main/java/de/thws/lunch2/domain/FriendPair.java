package de.thws.lunch2.domain;

public record FriendPair(
        String id, String inviterId, String inviteeId, PairStatus status, ReasonCode issue) {}
