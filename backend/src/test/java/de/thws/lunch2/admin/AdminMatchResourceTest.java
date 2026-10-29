package de.thws.lunch2.admin;

import de.thws.lunch2.common.MensaService;
import de.thws.lunch2.common.SlotService;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.domain.Invite;
import de.thws.lunch2.domain.ConnectionChoice;
import de.thws.lunch2.domain.ConnectChoice;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import de.thws.lunch2.persistence.InviteRepository;
import de.thws.lunch2.persistence.ConnectionRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AdminMatchResourceTest {
    @Inject ParticipantRepository participants;
    @Inject GroupRepository groups;
    @Inject FriendPairRepository pairs;
    @Inject DataAdmin data;
    @Inject MensaService mensas;
    @Inject SlotService slots;
    @Inject InviteRepository invites;
    @Inject ConnectionRepository connections;
    @Inject @ConfigProperty(name = "lunch2.admin-key") String adminKey;

    @BeforeEach
    @AfterEach
    void clearFixtures() {
        data.wipeAll();
    }

    @Test
    void fourApiRegistrationsAreMatchedAndSecondRunCreatesNoDuplicates() {
        List<String> ids = List.of(register("Lena", "DOMESTIC"), register("Sam", "DOMESTIC"),
                register("Aiko", "INTERNATIONAL"), register("Omar", "INTERNATIONAL"));
        JsonPath result = runMatch();
        assertEquals(Set.of("groupsCreated", "waiting"), result.getMap("").keySet());
        assertEquals(1, result.getList("groupsCreated").size());
        assertEquals(List.of(), result.getList("waiting"));
        assertEquals(Set.of("groupId", "campus", "slotId", "memberNames", "score"),
                result.getMap("groupsCreated[0]").keySet());
        String groupId = result.getString("groupsCreated[0].groupId");
        assertEquals(4, UUID.fromString(groupId).version());
        LunchGroup group = groups.findById(groupId).orElseThrow();
        assertEquals(ids.stream().sorted().toList(), group.memberIds());
        assertEquals(mensas.location(mensas.resolve(group.campus(), null)), group.location());
        assertEquals(List.of("travel"), group.sharedInterests());
        assertEquals(List.of("en"), group.commonLanguages());
        assertEquals(List.of("4 of you love Travel", "Everyone speaks English"), group.whyThisGroup());
        assertNull(group.cards());
        for (String id : ids) {
            Participant saved = participants.findById(id).orElseThrow();
            assertEquals(MatchStatus.MATCHED, saved.matchStatus());
            assertEquals(groupId, saved.groupId());
            assertNull(saved.waitingReason());
            assertEquals(List.of("travel"), saved.interests());
            assertNotNull(saved.tokenHash());
            assertNotNull(saved.consentAt());
        }
        assertEquals(List.of(), runMatch().getList("groupsCreated"));
        assertEquals(1, groups.findAll().size());
        assertEquals(group, groups.findById(groupId).orElseThrow());
    }

    @Test
    void missingOrWrongAdminKeyIsRejectedWithoutMutations() {
        register("Lena", "DOMESTIC");
        given().when().post("/api/admin/match").then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().header("X-Admin-Key", "wrong").when().post("/api/admin/match")
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        assertTrue(groups.findAll().isEmpty());
        assertEquals(ReasonCode.WAITING_FOR_NEXT_ROUND, participants.findAll().getFirst().waitingReason());
    }

    @Test
    void waitingReasonsArePersistedAndReturnedWithContractFields() {
        String first = register("Lena", "DOMESTIC");
        String second = register("Sam", "DOMESTIC");
        JsonPath result = runMatch();
        assertEquals(List.of(), result.getList("groupsCreated"));
        List<Map<String, Object>> waiting = result.getList("waiting");
        assertEquals(2, waiting.size());
        assertEquals(Set.of(first, second), waiting.stream().map(w -> w.get("participantId")).collect(java.util.stream.Collectors.toSet()));
        for (Map<String, Object> item : waiting) {
            assertEquals(Set.of("participantId", "displayName", "reasonCode"), item.keySet());
            assertEquals("NOT_ENOUGH_INTERNATIONAL", item.get("reasonCode"));
            Participant saved = participants.findById((String) item.get("participantId")).orElseThrow();
            assertEquals(MatchStatus.WAITING, saved.matchStatus());
            assertEquals(ReasonCode.NOT_ENOUGH_INTERNATIONAL, saved.waitingReason());
            assertNull(saved.groupId());
        }
    }

    @Test
    void activeFriendPairIsMappedAndExplanationUsesNamesAndLabels() {
        String lena = register("Lena", "DOMESTIC");
        String sam = register("Sam", "DOMESTIC");
        link(lena, sam, PairStatus.ACTIVE);
        register("Aiko", "INTERNATIONAL");
        register("Omar", "INTERNATIONAL");
        runMatch();
        LunchGroup group = groups.findAll().getFirst();
        assertEquals(23, group.score());
        assertTrue(group.whyThisGroup().contains(lena.compareTo(sam) < 0
                ? "Lena & Sam signed up together" : "Sam & Lena signed up together"));
        assertNotNull(participants.findById(lena).orElseThrow().friendPairId());
    }

    @Test
    void inactiveFriendPairDoesNotConstrainMatchingOrAddABonus() {
        String lena = register("Lena", "DOMESTIC");
        String absent = register("Sam", "DOMESTIC");
        link(lena, absent, PairStatus.INCOMPATIBLE);
        participants.delete(absent);
        register("Mia", "DOMESTIC");
        register("Aiko", "INTERNATIONAL");
        register("Omar", "INTERNATIONAL");
        runMatch();
        assertEquals(1, groups.findAll().size());
        assertEquals(13, groups.findAll().getFirst().score());
        assertTrue(groups.findAll().getFirst().whyThisGroup().stream().noneMatch(s -> s.contains("signed up together")));
    }

    @Test
    void previousGroupPairsApplyASoftScorePenalty() {
        String lena = register("Lena", "DOMESTIC");
        String sam = register("Sam", "DOMESTIC");
        String aiko = register("Aiko", "INTERNATIONAL");
        String omar = register("Omar", "INTERNATIONAL");
        var p = participants.findById(lena).orElseThrow();
        groups.save(new LunchGroup(UUID.randomUUID().toString(), p.campus(), p.slotIds().getFirst(), "Historical",
                List.of(lena, sam, aiko, omar).stream().sorted().toList(), List.of(), List.of("en"), List.of(),
                0, Instant.EPOCH, null));
        JsonPath result = runMatch();
        assertEquals(1, result.getList("groupsCreated").size());
        assertEquals(-35, result.getInt("groupsCreated[0].score"));
        assertEquals(2, groups.findAll().size());
    }

    @Test
    void authorizedResetReturns204AndClearsAllData() {
        String lena = register("Lena", "DOMESTIC");
        String sam = register("Sam", "DOMESTIC");
        link(lena, sam, PairStatus.ACTIVE);
        register("Aiko", "INTERNATIONAL");
        register("Omar", "INTERNATIONAL");
        runMatch();
        String groupId = groups.findAll().getFirst().id();
        invites.save(new Invite("TESTRESET1", lena, Instant.now(), null));
        connections.save(new ConnectionChoice(groupId, lena, sam, ConnectChoice.YES, "demo contact"));
        given().header("X-Admin-Key", adminKey).when().post("/api/admin/reset")
                .then().statusCode(204).body(is(""));
        assertTrue(participants.findAll().isEmpty());
        assertTrue(groups.findAll().isEmpty());
        assertTrue(pairs.findAll().isEmpty());
        assertTrue(invites.findByCode("TESTRESET1").isEmpty());
        assertTrue(connections.findByGroup(groupId).isEmpty());
    }

    @Test
    void unauthorizedResetCannotWipeData() {
        String id = register("Lena", "DOMESTIC");
        given().when().post("/api/admin/reset").then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().header("X-Admin-Key", "wrong").when().post("/api/admin/reset")
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        assertTrue(participants.findById(id).isPresent());
    }

    private String register(String name, String category) {
        return given().contentType(ContentType.JSON).body(Map.of("displayName", name, "category", category,
                "campus", "WUERZBURG", "languages", List.of("en"), "interests", List.of("travel"),
                "slotIds", List.of(slots.slots().getFirst().id()), "consent", true))
                .when().post("/api/participants").then().statusCode(201).extract().path("me.id");
    }

    private JsonPath runMatch() {
        return given().header("X-Admin-Key", adminKey).when().post("/api/admin/match")
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath();
    }

    private void link(String first, String second, PairStatus status) {
        String pairId = UUID.randomUUID().toString();
        pairs.save(new FriendPair(pairId, first, second, status, null));
        for (String id : List.of(first, second)) {
            Participant p = participants.findById(id).orElseThrow();
            participants.save(new Participant(p.id(), p.tokenHash(), p.displayName(), p.category(), p.campus(),
                    p.languages(), p.interests(), p.slotIds(), p.studyField(), p.matchStatus(), p.groupId(),
                    p.waitingReason(), pairId, p.createdAt(), p.consentAt()));
        }
    }
}
