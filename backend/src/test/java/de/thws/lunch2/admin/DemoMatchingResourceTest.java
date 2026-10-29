package de.thws.lunch2.admin;

import de.thws.lunch2.common.MensaService;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DemoMatchingResourceTest {
    @Inject DataAdmin data;
    @Inject ParticipantRepository participants;
    @Inject GroupRepository groups;
    @Inject MensaService mensas;
    @Inject @ConfigProperty(name = "lunch2.admin-key") String adminKey;

    @BeforeEach
    @AfterEach
    void clear() { data.wipeAll(); }

    @Test
    void aSingleLiveRegistrationCanRevealAValidGroupAtEveryMensaForEitherCategory() {
        for (var mensa : mensas.mensas()) for (Category category : Category.values()) {
            data.wipeAll();
            String slot = mensa.slotIds().getLast();
            JsonPath live = register("Live student", category, mensa, slot, "tr", null);
            String token = live.getString("accessToken");
            given().header("X-Admin-Key", adminKey).post("/api/admin/match").then()
                    .statusCode(200).body("groupsCreated.size()", is(0));
            assertEquals(1, participants.findAll().size(), "Ordinary matching must not create fictional people");
            JsonPath result = complete();
            assertEquals(1, result.getList("groupsCreated").size());
            assertEquals(4, participants.findAll().size());
            String groupId = result.getString("groupsCreated[0].groupId");
            given().auth().oauth2(token).get("/api/me").then().statusCode(200)
                    .body("matchStatus", is("MATCHED")).body("groupId", is(groupId));
            given().auth().oauth2(token).get("/api/groups/" + groupId).then().statusCode(200)
                    .body("members.size()", is(4)).body("commonLanguages", is(List.of("tr")));
            LunchGroup group = groups.findById(groupId).orElseThrow();
            assertEquals(2, group.memberIds().stream().map(id -> participants.findById(id).orElseThrow())
                    .filter(p -> p.category() == Category.DOMESTIC).count());
            assertTrue(group.memberIds().stream().map(id -> participants.findById(id).orElseThrow())
                    .allMatch(p -> mensa.id().equals(p.mensaId()) && p.slotIds().contains(slot)));
            assertEquals(List.of(), complete().getList("groupsCreated"));
            assertEquals(4, participants.findAll().size(), "A second action must not create duplicates");
        }
    }

    @Test
    void reusesCompatibleLiveRegistrationsInsteadOfGivingEachTheirOwnFakeGroup() {
        var mensa = mensas.mensas().getFirst();
        String slot = mensa.slotIds().getFirst();
        JsonPath a = register("Live domestic", Category.DOMESTIC, mensa, slot, "en", null);
        JsonPath b = register("Live international", Category.INTERNATIONAL, mensa, slot, "en", null);
        complete();
        assertEquals(1, groups.findAll().size());
        assertEquals(4, participants.findAll().size());
        assertTrue(groups.findAll().getFirst().memberIds().containsAll(List.of(a.getString("me.id"), b.getString("me.id"))));
    }

    @Test
    void fourCompatibleLivePeopleDoNotNeedAnyFictionalSeats() {
        var mensa = mensas.mensas().getFirst();
        for (int n = 0; n < 4; n++) register("Student " + n, n < 2 ? Category.DOMESTIC : Category.INTERNATIONAL,
                mensa, mensa.slotIds().getFirst(), "en", null);
        complete();
        assertEquals(4, participants.findAll().size());
        assertEquals(1, groups.findAll().size());
    }

    @Test
    void liveRegistrationCanBeMatchedAfterSeedGroupsHaveAlreadyBeenCommitted() {
        given().header("X-Admin-Key", adminKey).post("/api/admin/seed").then().statusCode(200);
        given().header("X-Admin-Key", adminKey).post("/api/admin/match").then().statusCode(200);
        var original = groups.findAll().getFirst();
        var mensa = mensas.mensas().getFirst();
        JsonPath live = register("New live student", Category.DOMESTIC, mensa, mensa.slotIds().getLast(), "en", null);
        complete();
        given().auth().oauth2(live.getString("accessToken")).get("/api/me").then().statusCode(200).body("matchStatus", is("MATCHED"));
        assertEquals(original, groups.findById(original.id()).orElseThrow());
        assertTrue(participants.findById(live.getString("me.id")).isPresent());
    }

    @Test
    void friendPairsStayTogetherWhenDemoSeatsAreAdded() {
        var mensa = mensas.mensas().getFirst();
        String slot = mensa.slotIds().getFirst();
        JsonPath a = register("Live friend one", Category.DOMESTIC, mensa, slot, "en", null);
        String invite = given().auth().oauth2(a.getString("accessToken")).post("/api/me/invite")
                .then().statusCode(201).extract().path("code");
        JsonPath b = register("Live friend two", Category.DOMESTIC, mensa, slot, "en", invite);
        complete();
        assertEquals(4, participants.findAll().size());
        assertTrue(groups.findAll().getFirst().memberIds().containsAll(List.of(a.getString("me.id"), b.getString("me.id"))));
    }

    @Test
    void incompatibleFriendsAreNotSplitOrForcedIntoADemoGroup() {
        var first = mensas.mensas().getFirst();
        var other = mensas.mensas().stream().filter(m -> m.campus() == first.campus() && !m.id().equals(first.id())).findFirst().orElseThrow();
        JsonPath a = register("Friend one", Category.DOMESTIC, first, first.slotIds().getFirst(), "en", null);
        String invite = given().auth().oauth2(a.getString("accessToken")).post("/api/me/invite")
                .then().statusCode(201).extract().path("code");
        register("Friend two", Category.DOMESTIC, other, other.slotIds().getFirst(), "en", invite);
        assertEquals(List.of(), complete().getList("groupsCreated"));
        assertEquals(2, participants.findAll().size());
    }

    @Test
    void demoCompletionRequiresAdminAuthorization() {
        given().post("/api/admin/match?fillWithDemo=true").then().statusCode(401);
        assertTrue(participants.findAll().isEmpty());
    }

    private JsonPath complete() {
        return given().header("X-Admin-Key", adminKey).post("/api/admin/match?fillWithDemo=true")
                .then().statusCode(200).extract().jsonPath();
    }

    private JsonPath register(String name, Category category, MensaService.Mensa mensa, String slot, String language, String invite) {
        Map<String, Object> request = new HashMap<>(Map.of("displayName", name, "category", category,
                "campus", mensa.campus(), "mensaId", mensa.id(), "slotIds", List.of(slot), "languages", List.of(language),
                "interests", List.of("travel"), "consent", true));
        if (invite != null) request.put("inviteCode", invite);
        return given().contentType(ContentType.JSON).body(request).post("/api/participants")
                .then().statusCode(201).extract().jsonPath();
    }
}
