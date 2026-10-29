package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.FriendPair;

import java.util.List;
import java.util.Optional;

public interface FriendPairRepository {
    Optional<FriendPair> findById(String id);
    void save(FriendPair p);
    void delete(String id);
    List<FriendPair> findAll();
}
