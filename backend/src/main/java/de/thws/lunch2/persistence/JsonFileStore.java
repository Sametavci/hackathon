package de.thws.lunch2.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.domain.ConnectionChoice;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.Invite;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.Participant;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/**
 * Single-instance snapshot persistence. Only one process may use a data file.
 * Each repository mutation writes a snapshot before returning; multi-repository operations are not transactions.
 * Domain values are immutable, so all changes must go through a repository's save method.
 */
@Startup
@ApplicationScoped
public class JsonFileStore {
    private final ConcurrentMap<String, Participant> participants = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LunchGroup> groups = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FriendPair> friendPairs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Invite> invites = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConnectionKey, ConnectionChoice> connections = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;
    private final Path dataFile;
    private final SnapshotMover snapshotMover;

    @Inject
    public JsonFileStore(ObjectMapper mapper, @ConfigProperty(name = "lunch2.data-file") String dataFile) {
        this(mapper, dataFile, JsonFileStore::moveSnapshot);
    }

    JsonFileStore(ObjectMapper mapper, String dataFile, SnapshotMover snapshotMover) {
        this.mapper = mapper;
        this.dataFile = Path.of(dataFile).toAbsolutePath().normalize();
        this.snapshotMover = snapshotMover;
        try {
            Files.createDirectories(this.dataFile.getParent());
            if (Files.exists(this.dataFile)) {
                restore(mapper.readValue(this.dataFile.toFile(), Snapshot.class));
                Log.info("JSON snapshot loaded");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load the JSON snapshot", exception);
        }
    }

    // Internal repository views. Access through methods also works with CDI client proxies.
    Map<String, Participant> participants() {
        return participants;
    }

    Map<String, LunchGroup> groups() {
        return groups;
    }

    Map<String, FriendPair> friendPairs() {
        return friendPairs;
    }

    Map<String, Invite> invites() {
        return invites;
    }

    Map<ConnectionKey, ConnectionChoice> connections() {
        return connections;
    }

    synchronized <K, V> Optional<V> find(Map<K, V> entries, K key) {
        return Optional.ofNullable(entries.get(key));
    }

    synchronized <V> List<V> findAll(Map<?, V> entries) {
        return List.copyOf(entries.values());
    }

    synchronized <K, V> void save(Map<K, V> entries, K key, V value) {
        mutate(() -> entries.put(key, value));
    }

    synchronized <K, V> void delete(Map<K, V> entries, K key) {
        mutate(() -> entries.remove(key));
    }

    synchronized <V> void deleteMatching(Map<?, V> entries, Predicate<V> predicate) {
        mutate(() -> entries.values().removeIf(predicate));
    }

    synchronized void wipeAll() {
        mutate(() -> {
            participants.clear();
            groups.clear();
            friendPairs.clear();
            invites.clear();
            connections.clear();
        });
        Log.info("JSON store reset");
    }

    private void mutate(Runnable mutation) {
        Snapshot before = snapshot();
        try {
            mutation.run();
            persist();
        } catch (RuntimeException exception) {
            restore(before);
            throw exception;
        }
    }

    private Snapshot snapshot() {
        return new Snapshot(List.copyOf(participants.values()), List.copyOf(groups.values()),
                List.copyOf(friendPairs.values()), List.copyOf(invites.values()), List.copyOf(connections.values()));
    }

    private void restore(Snapshot snapshot) {
        participants.clear();
        snapshot.participants().forEach(value -> participants.put(value.id(), value));
        groups.clear();
        snapshot.groups().forEach(value -> groups.put(value.id(), value));
        friendPairs.clear();
        snapshot.friendPairs().forEach(value -> friendPairs.put(value.id(), value));
        invites.clear();
        snapshot.invites().forEach(value -> invites.put(value.code(), value));
        connections.clear();
        snapshot.connections().forEach(value -> connections.put(ConnectionKey.of(value), value));
    }

    private void persist() {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(dataFile.getParent(), "lunch2-", ".tmp");
            mapper.writeValue(temporary.toFile(), snapshot());
            replaceSnapshot(temporary);
        } catch (IOException exception) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new UncheckedIOException("Could not write the JSON snapshot", exception);
        }
    }

    private void replaceSnapshot(Path temporary) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                snapshotMover.move(temporary, dataFile);
                return;
            } catch (AccessDeniedException exception) {
                // Windows can briefly deny replacement while another process has the file open.
                if (attempt >= 4) {
                    throw exception;
                }
                try {
                    Thread.sleep(20L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while replacing the JSON snapshot", interrupted);
                }
            }
        }
    }

    private static void moveSnapshot(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    interface SnapshotMover {
        void move(Path source, Path target) throws IOException;
    }

    record ConnectionKey(String groupId, String fromId, String toId) {
        static ConnectionKey of(ConnectionChoice choice) {
            return new ConnectionKey(choice.groupId(), choice.fromId(), choice.toId());
        }
    }

    public record Snapshot(List<Participant> participants, List<LunchGroup> groups,
                           List<FriendPair> friendPairs, List<Invite> invites, List<ConnectionChoice> connections) {
        public Snapshot {
            participants = List.copyOf(participants);
            groups = List.copyOf(groups);
            friendPairs = List.copyOf(friendPairs);
            invites = List.copyOf(invites);
            connections = List.copyOf(connections);
        }
    }
}
