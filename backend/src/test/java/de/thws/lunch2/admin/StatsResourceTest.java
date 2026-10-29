package de.thws.lunch2.admin;

import de.thws.lunch2.domain.ConnectChoice;
import de.thws.lunch2.domain.ConnectionChoice;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.persistence.ConnectionRepository;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class StatsResourceTest {
    @Inject DataAdmin data;
    @Inject GroupRepository groups;
    @Inject FriendPairRepository pairs;
    @Inject ParticipantRepository participants;
    @Inject ConnectionRepository connections;
    @Inject @ConfigProperty(name = "lunch2.admin-key") String adminKey;

    @BeforeEach
    @AfterEach
    void clear() { data.wipeAll(); }

    @Test
    void emptyStoreReturnsAllContractFieldsAndZeroCounts() {
        JsonPath result = stats();
        assertEquals(Set.of("registrations", "byCampus", "matched", "waiting", "matchRate", "groups", "friendPairs", "mutualConnections"), result.getMap("").keySet());
        assertEquals(Map.of("DOMESTIC", 0, "INTERNATIONAL", 0), result.getMap("registrations"));
        assertEquals(Map.of("WUERZBURG", 0, "SCHWEINFURT", 0), result.getMap("byCampus"));
        for (String key : Set.of("matched", "waiting", "groups", "friendPairs", "mutualConnections")) assertEquals(0, result.getInt(key));
        assertEquals(0, result.getDouble("matchRate"));
    }

    @Test
    void seededMatchReturnsCorrectAggregatesWithoutPrivateData() {
        seedAndMatch();
        JsonPath result = stats();
        assertEquals(Map.of("DOMESTIC", 9, "INTERNATIONAL", 9), result.getMap("registrations"));
        assertEquals(Map.of("WUERZBURG", 13, "SCHWEINFURT", 5), result.getMap("byCampus"));
        assertEquals(4, result.getInt("matched"));
        assertEquals(14, result.getInt("waiting"));
        assertEquals(0.22, result.getDouble("matchRate"));
        assertEquals(1, result.getInt("groups"));
        assertEquals(2, result.getInt("friendPairs"));
        assertEquals(0, result.getInt("mutualConnections"));
        assertFalse(result.prettify().contains("Mia"));
        assertFalse(result.prettify().contains("restoreUrl"));
    }

    @Test
    void mutualConnectionsCountOnceAndExcludeOneSidedNoAndDeletedMembers() {
        seedAndMatch();
        var group = groups.findAll().getFirst();
        String a = group.memberIds().get(0);
        String b = group.memberIds().get(1);
        connections.save(new ConnectionChoice(group.id(), a, b, ConnectChoice.YES, null));
        assertEquals(0, stats().getInt("mutualConnections"));
        connections.save(new ConnectionChoice(group.id(), b, a, ConnectChoice.YES, null));
        assertEquals(1, stats().getInt("mutualConnections"));
        connections.save(new ConnectionChoice(group.id(), b, a, ConnectChoice.NO, null));
        assertEquals(0, stats().getInt("mutualConnections"));
        connections.save(new ConnectionChoice(group.id(), b, a, ConnectChoice.YES, null));
        participants.delete(b);
        assertEquals(0, stats().getInt("mutualConnections"));
    }

    @Test
    void unlinkedPairsAreNotCounted() {
        seedAndMatch();
        var pair = pairs.findAll().getFirst();
        pairs.save(new FriendPair(pair.id(), pair.inviterId(), pair.inviteeId(), PairStatus.UNLINKED, null));
        assertEquals(1, stats().getInt("friendPairs"));
    }

    @Test
    void unauthorizedRequestsCannotReadStats() {
        given().when().get("/api/admin/stats").then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().header("X-Admin-Key", "wrong").when().get("/api/admin/stats")
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
    }

    private void seedAndMatch() {
        given().header("X-Admin-Key", adminKey).when().post("/api/admin/seed").then().statusCode(200);
        given().header("X-Admin-Key", adminKey).when().post("/api/admin/match").then().statusCode(200);
    }

    private JsonPath stats() {
        return given().header("X-Admin-Key", adminKey).when().get("/api/admin/stats")
                .then().statusCode(200).extract().jsonPath();
    }
}
