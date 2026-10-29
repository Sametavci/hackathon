package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.Participant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JsonParticipantRepository implements ParticipantRepository {
    private final JsonFileStore store;

    @Inject
    public JsonParticipantRepository(JsonFileStore store) {
        this.store = store;
    }

    @Override
    public Optional<Participant> findById(String id) {
        return store.find(store.participants(), id);
    }

    @Override
    public Optional<Participant> findByTokenHash(String h) {
        return findAll().stream().filter(participant -> participant.tokenHash().equals(h)).findFirst();
    }

    @Override
    public List<Participant> findAll() {
        return store.findAll(store.participants());
    }

    @Override
    public void save(Participant p) {
        store.save(store.participants(), p.id(), p);
    }

    @Override
    public void delete(String id) {
        store.delete(store.participants(), id);
    }
}
