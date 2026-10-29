package de.thws.lunch2.persistence;

import de.thws.lunch2.domain.Invite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.Optional;

@ApplicationScoped
public class JsonInviteRepository implements InviteRepository {
    private final JsonFileStore store;

    @Inject
    public JsonInviteRepository(JsonFileStore store) {
        this.store = store;
    }

    @Override
    public Optional<Invite> findByCode(String c) {
        return store.find(store.invites(), c);
    }

    @Override
    public Optional<Invite> findUnusedByInviter(String id) {
        return store.findAll(store.invites()).stream()
                .filter(invite -> invite.inviterId().equals(id) && invite.usedById() == null)
                .min(Comparator.comparing(Invite::createdAt).thenComparing(Invite::code));
    }

    @Override
    public void save(Invite i) {
        store.save(store.invites(), i.code(), i);
    }

    @Override
    public void deleteByInviter(String id) {
        store.deleteMatching(store.invites(), invite -> invite.inviterId().equals(id));
    }
}
