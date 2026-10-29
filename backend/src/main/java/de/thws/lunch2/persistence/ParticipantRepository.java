package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.Participant;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository {
    Optional<Participant> findById(String id);
    Optional<Participant> findByTokenHash(String h);
    List<Participant> findAll();
    void save(Participant p);
    void delete(String id);
}
