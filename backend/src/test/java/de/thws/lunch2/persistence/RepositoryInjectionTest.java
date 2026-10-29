package de.thws.lunch2.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.domain.Campus;
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
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class RepositoryInjectionTest {
    @Inject
    ParticipantRepository participants;
    @Inject
    GroupRepository groups;
    @Inject
    FriendPairRepository pairs;
    @Inject
    InviteRepository invites;
    @Inject
    ConnectionRepository connections;
    @Inject
    ObjectMapper mapper;
    @Inject
    @ConfigProperty(name = "lunch2.data-file")
    String dataFile;

    @Test
    void injectedRepositoriesShareTheConfiguredStoreAndPersistThroughCdiProxies() {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Participant participant = new Participant(id, "test-hash-" + id, "Test", Category.DOMESTIC, Campus.WUERZBURG,
                List.of("en"), List.of("travel"), List.of("2026-11-03T12:15"), null, MatchStatus.WAITING,
                null, ReasonCode.WAITING_FOR_NEXT_ROUND, null, now, now);
        FriendPair pair = new FriendPair(id, id, "friend", PairStatus.ACTIVE, null);
        Invite invite = new Invite(id, id, now, null);
        LunchGroup group = new LunchGroup(id, Campus.WUERZBURG, "2026-11-03T12:15", "Mensa",
                List.of(id, "b", "c", "d"), List.of("travel"), List.of("en"), List.of(), 0, now, null);
        ConnectionChoice choice = new ConnectionChoice(id, id, "b", ConnectChoice.YES, null);
        participants.save(participant);
        groups.save(group);
        pairs.save(pair);
        invites.save(invite);
        connections.save(choice);

        JsonFileStore restarted = new JsonFileStore(mapper, dataFile);
        assertEquals(participant, new JsonParticipantRepository(restarted).findById(id).orElseThrow());
        assertEquals(group, new JsonGroupRepository(restarted).findById(id).orElseThrow());
        assertEquals(pair, new JsonFriendPairRepository(restarted).findById(id).orElseThrow());
        assertEquals(invite, new JsonInviteRepository(restarted).findByCode(id).orElseThrow());
        assertEquals(choice, new JsonConnectionRepository(restarted).find(id, id, "b").orElseThrow());
    }
}
