package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.LunchGroup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JsonGroupRepository implements GroupRepository {
    private final JsonFileStore store;

    @Inject
    public JsonGroupRepository(JsonFileStore store) {
        this.store = store;
    }

    @Override
    public Optional<LunchGroup> findById(String id) {
        return store.find(store.groups(), id);
    }

    @Override
    public List<LunchGroup> findAll() {
        return store.findAll(store.groups());
    }

    @Override
    public void save(LunchGroup g) {
        store.save(store.groups(), g.id(), g);
    }
}
