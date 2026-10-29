package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.ConnectionChoice;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JsonConnectionRepository implements ConnectionRepository {
    private final JsonFileStore store;

    @Inject
    public JsonConnectionRepository(JsonFileStore store) {
        this.store = store;
    }

    @Override
    public List<ConnectionChoice> findByGroup(String groupId) {
        return store.findAll(store.connections()).stream()
                .filter(choice -> choice.groupId().equals(groupId)).toList();
    }

    @Override
    public Optional<ConnectionChoice> find(String groupId, String fromId, String toId) {
        return store.find(store.connections(), new JsonFileStore.ConnectionKey(groupId, fromId, toId));
    }

    @Override
    public void save(ConnectionChoice c) {
        store.save(store.connections(), JsonFileStore.ConnectionKey.of(c), c);
    }

    @Override
    public void deleteByParticipant(String participantId) {
        store.deleteMatching(store.connections(),
                choice -> choice.fromId().equals(participantId) || choice.toId().equals(participantId));
    }
}
