package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.ConnectionChoice;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository {
    List<ConnectionChoice> findByGroup(String groupId);
    Optional<ConnectionChoice> find(String groupId, String fromId, String toId);
    void save(ConnectionChoice c);
    void deleteByParticipant(String participantId);
}
