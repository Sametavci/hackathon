package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.Invite;

import java.util.Optional;

public interface InviteRepository {
    Optional<Invite> findByCode(String c);
    Optional<Invite> findUnusedByInviter(String id);
    void save(Invite i);
    void deleteByInviter(String id);
}
