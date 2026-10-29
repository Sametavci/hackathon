package de.thws.lunch2.connect;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.auth.TokenService;
import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.ConnectChoice;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.persistence.ConnectionRepository;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.JsonConnectionRepository;
import de.thws.lunch2.persistence.JsonFileStore;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ConnectionResourceTest.Profile.class)
class ConnectionResourceTest {
    @Inject
    DataAdmin admin;
    @Inject
    ParticipantRepository participants;
    @Inject
    GroupRepository groups;
    @Inject
    ConnectionRepository connections;
    @Inject
    TokenService tokens;
    @Inject
    ObjectMapper mapper;
    @Inject
    @ConfigProperty(name = "lunch2.data-file")
    String dataFile;

    private String groupId;
    private String collection;
    private Identity a;
    private Identity b;
    private Identity c;
    private Identity d;
    private Identity outsider;

    @BeforeEach
    void seedIsolatedGroup() {
        admin.wipeAll();
        groupId = UUID.randomUUID().toString();
        collection = "/api/groups/" + groupId + "/connections";
        a = create("Lena", Category.DOMESTIC, groupId);
        b = create("Sam", Category.DOMESTIC, groupId);
        c = create("Aiko", Category.INTERNATIONAL, groupId);
        d = create("Omar", Category.INTERNATIONAL, groupId);
        outsider = create("Other", Category.DOMESTIC, UUID.randomUUID().toString());
        groups.save(new LunchGroup(groupId, Campus.WUERZBURG, "2026-11-03T12:15", "Mensa",
                List.of(a.id(), b.id(), c.id(), d.id()).stream().sorted().toList(),
                List.of("travel"), List.of("en"), List.of(), 0, Instant.now(), null));
    }

    @AfterEach
    void clearIsolatedStore() {
        admin.wipeAll();
    }

    @Test
    void oneSidedYesIsHiddenFromRecipientAndListContainsOnlyOtherThreeMembers() {
        choose(a, b, "YES").then().statusCode(200)
                .body("myChoice", is("YES")).body("status", is("WAITING_FOR_THEM"));

        List<Map<String, Object>> list = given().auth().oauth2(a.token()).when().get(collection)
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath().getList("");
        assertEquals(3, list.size());
        assertEquals(Set.of(b.id(), c.id(), d.id()), Set.copyOf(list.stream().map(item -> item.get("participantId")).toList()));
        for (Map<String, Object> item : list) {
            assertEquals(Set.of("participantId", "displayName", "myChoice", "status", "myContact", "theirContact"), item.keySet());
            assertNull(item.get("myContact"));
            assertNull(item.get("theirContact"));
        }
        assertEquals("WAITING_FOR_THEM", item(a, b).get("status"));
        Map<String, Object> recipient = item(b, a);
        assertEquals("NONE", recipient.get("status"));
        assertTrue(recipient.containsKey("myChoice"));
        assertNull(recipient.get("myChoice"));
        assertNull(recipient.get("theirContact"));
    }

    @Test
    void yesThenNoIsIndistinguishableFromNoAnswerForTheRequester() {
        choose(a, b, "YES").then().statusCode(200);
        Map<String, Object> before = item(a, b);

        choose(b, a, "NO").then().statusCode(200).body("myChoice", is("NO")).body("status", is("NONE"));

        assertEquals(before, item(a, b));
        assertEquals("WAITING_FOR_THEM", item(a, b).get("status"));
        assertNull(item(a, b).get("theirContact"));
    }

    @Test
    void contactsRequireMutualYesAndExplicitSharingBeforeRecipientSeesThem() {
        share(a, b, "insta @lena.w").then().statusCode(409).body("error.code", is("NOT_MUTUAL"));
        choose(a, b, "YES").then().statusCode(200);
        share(a, b, "insta @lena.w").then().statusCode(409).body("error.code", is("NOT_MUTUAL"));
        choose(b, a, "YES").then().statusCode(200).body("status", is("MUTUAL"));
        assertNull(item(b, a).get("theirContact"));

        share(a, b, "insta @lena.w").then().statusCode(200).body("myContact", is("insta @lena.w"))
                .body("status", is("MUTUAL"));

        assertEquals("insta @lena.w", item(b, a).get("theirContact"));
        assertNull(item(a, b).get("theirContact"));
        assertEquals("NONE", item(c, a).get("status"));
        assertNull(item(c, a).get("theirContact"));
        share(a, c, "private to Sam").then().statusCode(409).body("error.code", is("NOT_MUTUAL"));
    }

    @Test
    void switchingToNoErasesMySharedContactAndImmediatelyHidesContactsFromBothSides() {
        mutual();
        share(a, b, "lena-contact").then().statusCode(200);
        share(b, a, "sam-contact").then().statusCode(200);

        choose(a, b, "NO").then().statusCode(200).body("status", is("NONE"))
                .body("myContact", is((Object) null)).body("theirContact", is((Object) null));

        assertEquals("WAITING_FOR_THEM", item(b, a).get("status"));
        assertNull(item(b, a).get("theirContact"));
        assertEquals("sam-contact", item(b, a).get("myContact"));
        assertNull(connections.find(groupId, a.id(), b.id()).orElseThrow().contact());
        choose(a, b, "YES").then().statusCode(200).body("status", is("MUTUAL"));
        assertNull(item(b, a).get("theirContact"));
        share(a, b, "new-explicit-share").then().statusCode(200);
        assertEquals("new-explicit-share", item(b, a).get("theirContact"));
    }

    @Test
    void nonMembersCannotReadOrMutateAndMembersCannotTargetAnOutsider() {
        given().auth().oauth2(outsider.token()).when().get(collection)
                .then().statusCode(403).body("error.code", is("FORBIDDEN"));
        choose(outsider, a, "YES").then().statusCode(403).body("error.code", is("FORBIDDEN"));
        share(outsider, a, "secret").then().statusCode(403).body("error.code", is("FORBIDDEN"));
        withdraw(outsider, a).then().statusCode(403).body("error.code", is("FORBIDDEN"));
        choose(a, outsider, "YES").then().statusCode(403).body("error.code", is("FORBIDDEN"));
        share(a, outsider, "secret").then().statusCode(403).body("error.code", is("FORBIDDEN"));
        withdraw(a, outsider).then().statusCode(403).body("error.code", is("FORBIDDEN"));
        assertTrue(connections.findByGroup(groupId).isEmpty());
    }

    @Test
    void contactWithdrawalIsIdempotentAndPreservesChoicesAndMutualStatus() {
        withdraw(a, b).then().statusCode(200).body("status", is("NONE")).body("myChoice", is((Object) null));
        assertTrue(connections.findByGroup(groupId).isEmpty());
        mutual();
        share(a, b, "lena-contact").then().statusCode(200);
        share(b, a, "sam-contact").then().statusCode(200);

        for (int repeat = 0; repeat < 2; repeat++) {
            withdraw(a, b).then().statusCode(200).body("status", is("MUTUAL"))
                    .body("myChoice", is("YES")).body("myContact", is((Object) null)).body("theirContact", is("sam-contact"));
            assertNull(item(b, a).get("theirContact"));
        }
        assertEquals(ConnectChoice.YES, connections.find(groupId, a.id(), b.id()).orElseThrow().choice());
    }

    @Test
    void invalidChoiceAndContactBodiesReturnValidationErrorsWithoutChangingSharedData() {
        mutual();
        share(a, b, "keep-contact").then().statusCode(200);
        for (Object choice : List.of("MAYBE", 123)) {
            given().auth().oauth2(a.token()).contentType(ContentType.JSON).body(Map.of("choice", choice))
                    .when().put(path(b)).then().statusCode(400).body("error.code", is("VALIDATION_FAILED"));
        }
        given().auth().oauth2(a.token()).contentType(ContentType.JSON).body(Map.of())
                .when().put(path(b)).then().statusCode(400).body("error.code", is("VALIDATION_FAILED"));
        Map<String, Object> nullContact = new LinkedHashMap<>();
        nullContact.put("contact", null);
        for (Map<String, Object> request : List.of(Map.<String, Object>of(), nullContact,
                Map.<String, Object>of("contact", ""), Map.<String, Object>of("contact", "a".repeat(101)))) {
            given().auth().oauth2(a.token()).contentType(ContentType.JSON).body(request)
                    .when().put(path(b) + "/contact").then().statusCode(400).contentType(ContentType.JSON)
                    .body("error.code", is("VALIDATION_FAILED"));
        }
        assertEquals("keep-contact", item(b, a).get("theirContact"));
        assertEquals("MUTUAL", item(a, b).get("status"));
    }

    @Test
    void contactLengthBoundariesAndFreeTextArePreservedExactly() {
        mutual();
        for (String contact : List.of("x", "a".repeat(100), "  discord: example  ")) {
            share(a, b, contact).then().statusCode(200).body("myContact", is(contact));
            assertEquals(contact, item(b, a).get("theirContact"));
        }
    }

    @Test
    void unmatchedCallerAndSelfTargetsAreRejected() {
        Identity waiting = create("Waiting", Category.DOMESTIC, null);
        choose(waiting, a, "YES").then().statusCode(409).body("error.code", is("NOT_MATCHED"));
        share(waiting, a, "secret").then().statusCode(409).body("error.code", is("NOT_MATCHED"));
        withdraw(waiting, a).then().statusCode(409).body("error.code", is("NOT_MATCHED"));
        choose(a, a, "YES").then().statusCode(400).body("error.code", is("VALIDATION_FAILED"));
        share(a, a, "secret").then().statusCode(400).body("error.code", is("VALIDATION_FAILED"));
        withdraw(a, a).then().statusCode(400).body("error.code", is("VALIDATION_FAILED"));
        assertTrue(connections.findByGroup(groupId).isEmpty());
    }

    @Test
    void allFourEndpointsRequireAuthenticationAndUnknownGroupsReturnNotFound() {
        given().when().get(collection).then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().contentType(ContentType.JSON).body(Map.of("choice", "YES")).when().put(path(b))
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().contentType(ContentType.JSON).body(Map.of("contact", "secret")).when().put(path(b) + "/contact")
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().when().delete(path(b) + "/contact").then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().auth().oauth2(a.token()).when().get("/api/groups/unknown/connections")
                .then().statusCode(404).body("error.code", is("NOT_FOUND"));
    }

    @Test
    void choicesAndSharedContactsSurviveReloadInTheirOriginalDirection() {
        mutual();
        share(a, b, "lena-contact").then().statusCode(200);
        var restarted = new JsonConnectionRepository(new JsonFileStore(mapper, dataFile));
        assertEquals(ConnectChoice.YES, restarted.find(groupId, a.id(), b.id()).orElseThrow().choice());
        assertEquals("lena-contact", restarted.find(groupId, a.id(), b.id()).orElseThrow().contact());
        assertNull(restarted.find(groupId, b.id(), a.id()).orElseThrow().contact());
    }

    @Test
    void participantDeletionRemovesChoicesAndContactsAndListUsesFormerParticipantTombstone() {
        mutual();
        share(a, b, "lena-contact").then().statusCode(200);
        share(b, a, "sam-contact").then().statusCode(200);

        given().auth().oauth2(b.token()).when().delete("/api/me").then().statusCode(204);

        Map<String, Object> former = item(a, b);
        assertEquals("Former participant", former.get("displayName"));
        assertEquals("NONE", former.get("status"));
        assertNull(former.get("myChoice"));
        assertNull(former.get("myContact"));
        assertNull(former.get("theirContact"));
        assertTrue(connections.findByGroup(groupId).isEmpty());
    }

    private void mutual() {
        choose(a, b, "YES").then().statusCode(200);
        choose(b, a, "YES").then().statusCode(200);
    }

    private Response choose(Identity caller, Identity target, String choice) {
        return given().auth().oauth2(caller.token()).contentType(ContentType.JSON).body(Map.of("choice", choice))
                .when().put(path(target));
    }

    private Response share(Identity caller, Identity target, String contact) {
        return given().auth().oauth2(caller.token()).contentType(ContentType.JSON).body(Map.of("contact", contact))
                .when().put(path(target) + "/contact");
    }

    private Response withdraw(Identity caller, Identity target) {
        return given().auth().oauth2(caller.token()).when().delete(path(target) + "/contact");
    }

    private String path(Identity target) {
        return collection + "/" + target.id();
    }

    private Map<String, Object> item(Identity caller, Identity target) {
        List<Map<String, Object>> response = given().auth().oauth2(caller.token()).when().get(collection)
                .then().statusCode(200).extract().jsonPath().getList("");
        return response.stream().filter(item -> target.id().equals(item.get("participantId"))).findFirst().orElseThrow();
    }

    private Identity create(String name, Category category, String assignedGroup) {
        String id = UUID.randomUUID().toString();
        String token = tokens.newToken();
        Instant now = Instant.now();
        participants.save(new Participant(id, tokens.hash(token), name, category, Campus.WUERZBURG,
                List.of("en"), List.of("travel"), List.of("2026-11-03T12:15"), null,
                assignedGroup == null ? MatchStatus.WAITING : MatchStatus.MATCHED, assignedGroup,
                assignedGroup == null ? ReasonCode.WAITING_FOR_NEXT_ROUND : null, null, now, now));
        return new Identity(id, token);
    }

    private record Identity(String id, String token) {}

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("lunch2.data-file", "target/test-data/hack107.json");
        }
    }
}
