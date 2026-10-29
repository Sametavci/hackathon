package de.thws.lunch2.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JsonDataAdmin implements DataAdmin {
    private final JsonFileStore store;

    @Inject
    public JsonDataAdmin(JsonFileStore store) {
        this.store = store;
    }

    @Override
    public void wipeAll() {
        store.wipeAll();
    }
}
