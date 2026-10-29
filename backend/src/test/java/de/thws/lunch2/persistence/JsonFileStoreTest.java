package de.thws.lunch2.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Card;
import de.thws.lunch2.domain.CardSet;
import de.thws.lunch2.domain.CardSource;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.ConnectChoice;
import de.thws.lunch2.domain.ConnectionChoice;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.Invite;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFileStoreTest {
    private static final Instant CREATED = Instant.parse("2026-10-29T09:00:00.123456789Z");

    @TempDir
    Path directory;

    @Test
    void missingSnapshotStartsEmptyAndCreatesParentDirectories() {
        Path file = directory.resolve("nested/data/lunch2.json");
        var repositories = open(file);

        assertTrue(Files.isDirectory(file.getParent()));
        assertEmpty(repositories);
        assertTrue(repositories.participants().findById("missing").isEmpty());
        assertTrue(repositories.participants().findByTokenHash("missing").isEmpty());
    }

    @Test
    void allDomainValuesSurviveRestartIncludingTimesNullsAndCachedCards() {
        Path file = directory.resolve("lunch2.json");
        var repositories = open(file);
        Participant participant = participant("a");
        FriendPair pair = new FriendPair("pair", "a", "b", PairStatus.INCOMPATIBLE,
                ReasonCode.FRIEND_DIFFERENT_CAMPUS);
        Invite invite = new Invite("K7Q2M9XA3P", "a", CREATED, null);
        LunchGroup group = group(new CardSet("en", CardSource.FALLBACK,
                List.of(new Card("c1", "Where would you travel?", "travel"))));
        ConnectionChoice connection = new ConnectionChoice("group", "a", "b", ConnectChoice.YES, "discord: example");
        repositories.participants().save(participant);
        repositories.groups().save(group);
        repositories.pairs().save(pair);
        repositories.invites().save(invite);
        repositories.connections().save(connection);

        var restarted = open(file);
        assertEquals(participant, restarted.participants().findById("a").orElseThrow());
        assertEquals(participant, restarted.participants().findByTokenHash("hash-a").orElseThrow());
        assertEquals(List.of(participant), restarted.participants().findAll());
        assertEquals(group, restarted.groups().findById("group").orElseThrow());
        assertEquals(List.of(group), restarted.groups().findAll());
        assertEquals(pair, restarted.pairs().findById("pair").orElseThrow());
        assertEquals(List.of(pair), restarted.pairs().findAll());
        assertEquals(invite, restarted.invites().findByCode(invite.code()).orElseThrow());
        assertEquals(invite, restarted.invites().findUnusedByInviter("a").orElseThrow());
        assertEquals(connection, restarted.connections().find("group", "a", "b").orElseThrow());
        assertEquals(List.of(connection), restarted.connections().findByGroup("group"));
    }

    @Test
    void updatesReplaceExistingValuesAndPersistImmediately() {
        Path file = directory.resolve("lunch2.json");
        var repositories = open(file);
        Participant original = participant("a");
        repositories.participants().save(original);
        Participant updated = new Participant(original.id(), "new-hash", original.displayName(), original.category(),
                original.campus(), original.languages(), original.interests(), original.slotIds(), "DESIGN",
                MatchStatus.MATCHED, "group", null, "pair", original.createdAt(), original.consentAt());
        repositories.participants().save(updated);
        assertEquals(List.of(updated), open(file).participants().findAll());
        assertTrue(repositories.participants().findByTokenHash("hash-a").isEmpty());
        assertEquals(updated, repositories.participants().findByTokenHash("new-hash").orElseThrow());

        repositories.groups().save(group(null));
        assertEquals(group(null), open(file).groups().findById("group").orElseThrow());
        LunchGroup cached = group(new CardSet("de", CardSource.AI, List.of(new Card("c1", "Hallo?", "generic"))));
        repositories.groups().save(cached);
        assertEquals(List.of(cached), open(file).groups().findAll());

        repositories.pairs().save(new FriendPair("pair", "a", "b", PairStatus.ACTIVE, null));
        FriendPair unlinked = new FriendPair("pair", "a", "b", PairStatus.UNLINKED, null);
        repositories.pairs().save(unlinked);
        assertEquals(List.of(unlinked), open(file).pairs().findAll());

        repositories.invites().save(new Invite("code", "a", CREATED, null));
        Invite used = new Invite("code", "a", CREATED, "b");
        repositories.invites().save(used);
        assertTrue(open(file).invites().findUnusedByInviter("a").isEmpty());
        assertEquals(used, open(file).invites().findByCode("code").orElseThrow());

        repositories.connections().save(new ConnectionChoice("group", "a", "b", ConnectChoice.YES, "contact"));
        ConnectionChoice withdrawn = new ConnectionChoice("group", "a", "b", ConnectChoice.NO, null);
        repositories.connections().save(withdrawn);
        assertEquals(List.of(withdrawn), open(file).connections().findByGroup("group"));
    }

    @Test
    void deletesPersistAndKeepUnrelatedValues() {
        Path file = directory.resolve("lunch2.json");
        var repositories = open(file);
        repositories.participants().save(participant("a"));
        repositories.participants().save(participant("b"));
        repositories.participants().delete("a");
        assertEquals(List.of(participant("b")), open(file).participants().findAll());

        FriendPair keep = new FriendPair("keep", "c", "d", PairStatus.ACTIVE, null);
        repositories.pairs().save(keep);
        repositories.pairs().save(new FriendPair("remove", "a", "b", PairStatus.ACTIVE, null));
        repositories.pairs().delete("remove");
        assertEquals(List.of(keep), open(file).pairs().findAll());

        Invite other = new Invite("other", "b", CREATED, null);
        repositories.invites().save(other);
        repositories.invites().save(new Invite("unused", "a", CREATED, null));
        repositories.invites().save(new Invite("used", "a", CREATED, "c"));
        repositories.invites().deleteByInviter("a");
        var restarted = open(file);
        assertTrue(restarted.invites().findByCode("unused").isEmpty());
        assertTrue(restarted.invites().findByCode("used").isEmpty());
        assertEquals(other, restarted.invites().findByCode("other").orElseThrow());
    }

    @Test
    void connectionsAreDirectionalAndScopedToGroupAndDeletionRemovesBothDirections() {
        Path file = directory.resolve("lunch2.json");
        var repositories = open(file);
        var outgoing = new ConnectionChoice("group", "a", "b", ConnectChoice.YES, "a-contact");
        var incoming = new ConnectionChoice("group", "b", "a", ConnectChoice.NO, null);
        var otherGroup = new ConnectionChoice("other-group", "b", "a", ConnectChoice.YES, "b-contact");
        var unrelated = new ConnectionChoice("group", "b", "c", ConnectChoice.YES, null);
        for (var choice : List.of(outgoing, incoming, otherGroup, unrelated)) {
            repositories.connections().save(choice);
        }

        var restarted = open(file);
        assertEquals(outgoing, restarted.connections().find("group", "a", "b").orElseThrow());
        assertEquals(incoming, restarted.connections().find("group", "b", "a").orElseThrow());
        assertEquals(otherGroup, restarted.connections().find("other-group", "b", "a").orElseThrow());
        assertTrue(restarted.connections().find("other-group", "a", "b").isEmpty());
        repositories.connections().deleteByParticipant("a");

        restarted = open(file);
        assertEquals(List.of(unrelated), restarted.connections().findByGroup("group"));
        assertTrue(restarted.connections().findByGroup("other-group").isEmpty());
    }

    @Test
    void wipeAllPersistsAnEmptySnapshotAcrossEveryRepository() {
        Path file = directory.resolve("lunch2.json");
        var repositories = open(file);
        repositories.participants().save(participant("a"));
        repositories.groups().save(group(null));
        repositories.pairs().save(new FriendPair("pair", "a", "b", PairStatus.ACTIVE, null));
        repositories.invites().save(new Invite("code", "a", CREATED, null));
        repositories.connections().save(new ConnectionChoice("group", "a", "b", ConnectChoice.YES, "contact"));

        repositories.admin().wipeAll();

        assertEmpty(repositories);
        assertEmpty(open(file));
    }

    @Test
    void concurrentSavesDoNotLoseEntriesOrCorruptSnapshot() throws Exception {
        Path file = directory.resolve("lunch2.json");
        var repositories = open(file);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> writes = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                String id = "p" + index;
                writes.add(executor.submit(() -> repositories.participants().save(participant(id))));
            }
            for (var write : writes) {
                write.get(10, TimeUnit.SECONDS);
            }
        }

        var restarted = open(file);
        assertEquals(32, restarted.participants().findAll().size());
        for (int index = 0; index < 32; index++) {
            assertEquals(participant("p" + index), restarted.participants().findById("p" + index).orElseThrow());
        }
    }

    @Test
    void corruptSnapshotFailsWithoutOverwritingIt() throws IOException {
        Path file = directory.resolve("lunch2.json");
        String corrupt = "{not valid JSON";
        Files.writeString(file, corrupt);

        assertThrows(UncheckedIOException.class, () -> open(file));
        assertEquals(corrupt, Files.readString(file));
    }

    @Test
    void failedWritesPreservePreviousSnapshotAndInMemoryValues() throws IOException {
        Path file = directory.resolve("lunch2.json");
        FailingMapper mapper = new FailingMapper();
        mapper.findAndRegisterModules();
        var repositories = new Repositories(new JsonFileStore(mapper, file.toString()));
        repositories.participants().save(participant("a"));
        String before = Files.readString(file);
        mapper.failWrites = true;

        assertThrows(UncheckedIOException.class, () -> repositories.participants().save(participant("b")));
        assertThrows(UncheckedIOException.class, () -> repositories.participants().delete("a"));
        assertThrows(UncheckedIOException.class, () -> repositories.admin().wipeAll());

        assertEquals(List.of(participant("a")), repositories.participants().findAll());
        assertEquals(before, Files.readString(file));
        assertEquals(List.of(participant("a")), open(file).participants().findAll());
        try (var files = Files.list(directory)) {
            assertEquals(1L, files.count(), "Temporary snapshots must be cleaned up after failure");
        }
    }

    @Test
    void domainListsAndRepositoryResultsCannotBeMutatedWithoutSave() {
        List<String> languages = new ArrayList<>(List.of("en"));
        Participant participant = new Participant("a", "hash-a", "Test", Category.DOMESTIC, Campus.WUERZBURG,
                languages, List.of("travel"), List.of("2026-11-03T12:15"), null,
                MatchStatus.WAITING, null, ReasonCode.WAITING_FOR_NEXT_ROUND, null, CREATED, CREATED);
        var repositories = open(directory.resolve("lunch2.json"));
        repositories.participants().save(participant);
        languages.add("de");

        assertEquals(List.of("en"), repositories.participants().findById("a").orElseThrow().languages());
        assertThrows(UnsupportedOperationException.class, () -> participant.languages().add("de"));
        assertThrows(UnsupportedOperationException.class, () -> repositories.participants().findAll().clear());
    }

    private Repositories open(Path file) {
        return new Repositories(new JsonFileStore(new ObjectMapper().findAndRegisterModules(), file.toString()));
    }

    @Test
    void transientSnapshotReplacementDenialsAreRetried() {
        Path file = directory.resolve("lunch2.json");
        AtomicInteger attempts = new AtomicInteger();
        var store = new JsonFileStore(new ObjectMapper().findAndRegisterModules(), file.toString(), (source, target) -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new AccessDeniedException(source.toString());
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        });
        var repositories = new Repositories(store);

        repositories.participants().save(participant("a"));

        assertTrue(attempts.get() > 1);
        assertEquals(List.of(participant("a")), repositories.participants().findAll());
        assertEquals(List.of(participant("a")), open(file).participants().findAll());
    }

    @Test
    void persistentReplacementDenialIsBoundedAndPreservesPreviousState() throws IOException {
        Path file = directory.resolve("lunch2.json");
        AtomicInteger attempts = new AtomicInteger();
        AtomicBoolean denied = new AtomicBoolean();
        var store = new JsonFileStore(new ObjectMapper().findAndRegisterModules(), file.toString(), (source, target) -> {
            attempts.incrementAndGet();
            if (denied.get()) {
                throw new AccessDeniedException(source.toString());
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        });
        var repositories = new Repositories(store);
        repositories.participants().save(participant("a"));
        String before = Files.readString(file);
        attempts.set(0);
        denied.set(true);

        assertThrows(UncheckedIOException.class, () -> repositories.participants().save(participant("b")));

        assertTrue(attempts.get() > 1 && attempts.get() < 10);
        assertEquals(before, Files.readString(file));
        assertEquals(List.of(participant("a")), repositories.participants().findAll());
        assertEquals(List.of(participant("a")), open(file).participants().findAll());
        try (var files = Files.list(directory)) {
            assertEquals(1L, files.count());
        }
    }

    private static void assertEmpty(Repositories repositories) {
        assertTrue(repositories.participants().findAll().isEmpty());
        assertTrue(repositories.groups().findAll().isEmpty());
        assertTrue(repositories.pairs().findAll().isEmpty());
        assertTrue(repositories.invites().findByCode("code").isEmpty());
        assertTrue(repositories.invites().findUnusedByInviter("a").isEmpty());
        assertTrue(repositories.connections().findByGroup("group").isEmpty());
    }

    private static Participant participant(String id) {
        return new Participant(id, "hash-" + id, "Test", Category.DOMESTIC, Campus.WUERZBURG,
                List.of("de", "en"), List.of("travel", "gaming"), List.of("2026-11-03T12:15"), null,
                MatchStatus.WAITING, null, ReasonCode.WAITING_FOR_NEXT_ROUND, null, CREATED, CREATED);
    }

    private static LunchGroup group(CardSet cards) {
        return new LunchGroup("group", Campus.WUERZBURG, "2026-11-03T12:15", "Mensa",
                List.of("a", "b", "c", "d"), List.of("travel"), List.of("en"), List.of("Everyone speaks English"),
                17, CREATED, cards);
    }

    private record Repositories(ParticipantRepository participants, GroupRepository groups,
                                FriendPairRepository pairs, InviteRepository invites,
                                ConnectionRepository connections, DataAdmin admin) {
        private Repositories(JsonFileStore store) {
            this(new JsonParticipantRepository(store), new JsonGroupRepository(store),
                    new JsonFriendPairRepository(store), new JsonInviteRepository(store),
                    new JsonConnectionRepository(store), new JsonDataAdmin(store));
        }
    }

    private static class FailingMapper extends ObjectMapper {
        private boolean failWrites;

        @Override
        public void writeValue(File file, Object value) throws IOException {
            if (failWrites) {
                throw new IOException("Simulated snapshot write failure");
            }
            super.writeValue(file, value);
        }
    }
}
