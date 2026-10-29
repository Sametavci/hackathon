package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.FriendPair;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JsonFriendPairRepository implements FriendPairRepository {
    private final JsonFileStore store;

    @Inject
    public JsonFriendPairRepository(JsonFileStore store) {
        this.store = store;
    }

    @Override
    public Optional<FriendPair> findById(String id) {
        return store.find(store.friendPairs(), id);
    }

    @Override
    public void save(FriendPair p) {
        store.save(store.friendPairs(), p.id(), p);
    }

    @Override
    public void delete(String id) {
        store.delete(store.friendPairs(), id);
    }

    @Override
    public List<FriendPair> findAll() {
        return store.findAll(store.friendPairs());
    }
}
