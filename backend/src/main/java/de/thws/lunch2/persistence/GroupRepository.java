package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.LunchGroup;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {
    Optional<LunchGroup> findById(String id);
    List<LunchGroup> findAll();
    void save(LunchGroup g);
}
