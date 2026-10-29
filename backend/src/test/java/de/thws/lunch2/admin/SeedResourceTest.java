package de.thws.lunch2.admin;

import de.thws.lunch2.auth.TokenService;
import de.thws.lunch2.common.SlotService;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SeedResourceTest {
    @Inject DataAdmin data;
    @Inject ParticipantRepository participants;
    @Inject GroupRepository groups;
    @Inject FriendPairRepository pairs;
    @Inject TokenService tokens;
    @Inject SlotService slots;
    @Inject @ConfigProperty(name = "lunch2.admin-key") String adminKey;
    @Inject @ConfigProperty(name = "lunch2.public-base-url") String publicBaseUrl;

    @BeforeEach
    @AfterEach
    void clearFixtures() { data.wipeAll(); }

    @Test
    void seedReturnsEighteenDemoIdentitiesWithUsableRestoreLinksAndOnlyStoresHashes() {
        JsonPath response = seed();
        assertEquals(Set.of("participants"), response.getMap("").keySet());
        List<Map<String, String>> people = response.getList("participants");
        assertEquals(18, people.size());
        assertEquals(18, participants.findAll().size());
        assertEquals(2, pairs.findAll().size());
        assertTrue(groups.findAll().isEmpty());
        for (Map<String, String> person : people) {
            assertEquals(Set.of("id", "displayName", "campus", "category", "restoreUrl"), person.keySet());
            assertEquals(4, UUID.fromString(person.get("id")).version());
            assertTrue(person.get("displayName").endsWith("(demo)"));
            String url = person.get("restoreUrl");
            assertTrue(url.startsWith(publicBaseUrl.replaceAll("/+$", "") + "/#/back?t="));
            String token = url.substring(url.indexOf("#/back?t=") + 9);
            assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
            var saved = participants.findById(person.get("id")).orElseThrow();
            assertEquals(tokens.hash(token), saved.tokenHash());
            assertEquals(MatchStatus.WAITING, saved.matchStatus());
            given().auth().oauth2(token).when().get("/api/me").then().statusCode(200).body("id", is(saved.id()));
        }
    }

    @Test
    void resetSeedMatchHasExactlyThePlannedGroupAndWaitingReasons() {
        given().header("X-Admin-Key", adminKey).when().post("/api/admin/reset").then().statusCode(204);
        seed();
        JsonPath result = match();
        assertEquals(1, result.getList("groupsCreated").size());
        List<String> names = result.getList("groupsCreated[0].memberNames");
        assertEquals(Set.of("Mia (demo)", "Sofia (demo)", "Paul (demo)", "Carlos (demo)"), Set.copyOf(names));
        Map<String, String> actual = waiting(result);
        assertEquals(Map.ofEntries(
                Map.entry("Aiko (demo)", "NOT_ENOUGH_DOMESTIC"), Map.entry("Omar (demo)", "NOT_ENOUGH_DOMESTIC"),
                Map.entry("Jonas (demo)", "NOT_ENOUGH_INTERNATIONAL"), Map.entry("Felix (demo)", "NOT_ENOUGH_INTERNATIONAL"),
                Map.entry("Emma (demo)", "NOT_ENOUGH_INTERNATIONAL"), Map.entry("Fatima (demo)", "NOT_ENOUGH_INTERNATIONAL"),
                Map.entry("Ben (demo)", "FRIEND_DIFFERENT_CAMPUS"), Map.entry("Zeynep (demo)", "FRIEND_DIFFERENT_CAMPUS"),
                Map.entry("Deniz (demo)", "NO_COMMON_LANGUAGE"), Map.entry("Leon (demo)", "NO_COMMON_LANGUAGE"),
                Map.entry("Nora (demo)", "NO_COMMON_LANGUAGE"), Map.entry("Yuki (demo)", "NO_COMMON_LANGUAGE"),
                Map.entry("Arjun (demo)", "NO_COMMON_LANGUAGE"), Map.entry("Lucia (demo)", "NO_COMMON_LANGUAGE")), actual);
        assertTrue(groups.findAll().getFirst().whyThisGroup().stream().anyMatch(text -> text.contains("signed up together")));
        assertEquals(4, participants.findAll().stream().filter(p -> p.matchStatus() == MatchStatus.MATCHED).count());
        assertEquals(List.of(), match().getList("groupsCreated"));
    }

    @Test
    void liveDomesticFriendPairMatchesOnlyWithAikoAndOmar() {
        seed();
        match();
        String secondDate = slots.slots().stream().map(SlotService.Slot::date).distinct().toList().get(1);
        String lenaToken = register("Lena", secondDate + "T12:15", null).getString("accessToken");
        String code = given().auth().oauth2(lenaToken).when().post("/api/me/invite")
                .then().statusCode(201).extract().path("code");
        register("Sam", secondDate + "T12:15", code);
        JsonPath result = match();
        assertEquals(1, result.getList("groupsCreated").size());
        assertEquals(Set.of("Lena", "Sam", "Aiko (demo)", "Omar (demo)"),
                Set.copyOf(result.<String>getList("groupsCreated[0].memberNames")));
        assertEquals(2, groups.findAll().size());
        String groupId = result.getString("groupsCreated[0].groupId");
        given().auth().oauth2(lenaToken).when().get("/api/me").then().statusCode(200)
                .body("matchStatus", is("MATCHED")).body("groupId", is(groupId));
        given().auth().oauth2(lenaToken).when().get("/api/groups/{id}", groupId).then().statusCode(200);
        assertFalse(waiting(result).containsKey("Aiko (demo)"));
        assertFalse(waiting(result).containsKey("Omar (demo)"));
    }

    @Test
    void repeatingSeedReturnsConflictWithoutChangingExistingData() {
        seed();
        var before = Set.copyOf(participants.findAll());
        given().header("X-Admin-Key", adminKey).when().post("/api/admin/seed").then()
                .statusCode(409).body("error.code", is("DEMO_NOT_EMPTY"));
        assertEquals(before, Set.copyOf(participants.findAll()));
        assertEquals(2, pairs.findAll().size());
    }

    @Test
    void seedRequiresAdminKeyAndDoesNotMutateDataWhenUnauthorized() {
        given().when().post("/api/admin/seed").then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().header("X-Admin-Key", "wrong").when().post("/api/admin/seed")
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        assertTrue(participants.findAll().isEmpty());
        assertTrue(pairs.findAll().isEmpty());
    }

    private JsonPath seed() {
        return given().header("X-Admin-Key", adminKey).when().post("/api/admin/seed")
                .then().statusCode(200).extract().jsonPath();
    }

    private JsonPath match() {
        return given().header("X-Admin-Key", adminKey).when().post("/api/admin/match")
                .then().statusCode(200).extract().jsonPath();
    }

    private Map<String, String> waiting(JsonPath response) {
        List<Map<String, String>> waiting = response.getList("waiting");
        return waiting.stream().collect(Collectors.toMap(p -> p.get("displayName"), p -> p.get("reasonCode")));
    }

    private JsonPath register(String name, String slot, String code) {
        var body = new java.util.HashMap<String, Object>(Map.of("displayName", name, "category", "DOMESTIC",
                "campus", "WUERZBURG", "languages", List.of("en", "de"), "interests", List.of("travel"),
                "slotIds", List.of(slot), "consent", true));
        if (code != null) body.put("inviteCode", code);
        return given().contentType(ContentType.JSON).body(body).when().post("/api/participants")
                .then().statusCode(201).extract().jsonPath();
    }
}
