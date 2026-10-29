package de.thws.lunch2.group;

import de.thws.lunch2.common.SlotService;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.GroupRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
class GroupResourceTest {
    @Inject DataAdmin data;
    @Inject GroupRepository groups;
    @Inject SlotService slots;
    @Inject @ConfigProperty(name = "lunch2.admin-key") String adminKey;
    private List<Identity> members;
    private String groupId;

    @BeforeEach
    void createGroupThroughTheApi() {
        data.wipeAll();
        Identity lena = register("Lena", "DOMESTIC", null);
        String invite = given().auth().oauth2(lena.token()).when().post("/api/me/invite")
                .then().statusCode(201).extract().path("code");
        Identity sam = register("Sam", "DOMESTIC", invite);
        members = List.of(lena, sam, register("Aiko", "INTERNATIONAL", null), register("Omar", "INTERNATIONAL", null));
        groupId = given().header("X-Admin-Key", adminKey).when().post("/api/admin/match")
                .then().statusCode(200).extract().path("groupsCreated[0].groupId");
        assertNotNull(groupId);
    }

    @AfterEach
    void cleanup() {
        data.wipeAll();
    }

    @Test
    void everyMemberCanFollowMeToTheirGroupWithPersonalizedFlags() {
        for (int i = 0; i < members.size(); i++) {
            Identity current = members.get(i);
            given().auth().oauth2(current.token()).when().get("/api/me").then()
                    .statusCode(200).body("matchStatus", is("MATCHED")).body("groupId", is(groupId));
            JsonPath response = read(current);
            assertEquals(groupId, response.getString("id"));
            List<Map<String, Object>> visible = response.getList("members");
            assertEquals(4, visible.size());
            assertEquals(groups.findById(groupId).orElseThrow().memberIds(), visible.stream().map(m -> m.get("id")).toList());
            for (Map<String, Object> member : visible) {
                String id = (String) member.get("id");
                assertEquals(id.equals(current.id()), member.get("isMe"));
                String friend = i == 0 ? members.get(1).id() : i == 1 ? members.getFirst().id() : null;
                assertEquals(id.equals(friend), member.get("isMyFriend"));
            }
        }
    }

    @Test
    void responseContainsOnlyContractFieldsAndNoPrivateMemberFields() {
        JsonPath response = read(members.getFirst());
        assertEquals(Set.of("id", "campus", "slot", "startsAt", "location", "members", "sharedInterests",
                "commonLanguages", "whyThisGroup"), response.getMap("").keySet());
        assertEquals(Set.of("id", "date", "start", "end", "label"), response.getMap("slot").keySet());
        List<Map<String, Object>> visible = response.getList("members");
        for (Map<String, Object> member : visible) {
            assertEquals(Set.of("id", "displayName", "isMe", "isMyFriend", "interests"), member.keySet());
            for (String forbidden : List.of("category", "languages", "studyField", "token", "tokenHash", "contact")) {
                assertFalse(member.containsKey(forbidden));
            }
        }
        LunchGroup saved = groups.findById(groupId).orElseThrow();
        assertEquals(saved.location(), response.getString("location"));
        assertEquals(saved.sharedInterests(), response.getList("sharedInterests"));
        assertEquals(saved.commonLanguages(), response.getList("commonLanguages"));
        assertEquals(saved.whyThisGroup(), response.getList("whyThisGroup"));
        SlotService.Slot slot = slots.slots().getFirst();
        assertEquals(Map.of("id", slot.id(), "date", slot.date(), "start", slot.start(), "end", slot.end(), "label", slot.label()),
                response.getMap("slot"));
        assertEquals(LocalDateTime.parse(slot.id()).atZone(ZoneId.of("Europe/Berlin"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), response.getString("startsAt"));
    }

    @Test
    void nonMemberReceives403WithoutGroupData() {
        Identity outsider = register("Outside", "DOMESTIC", null);
        given().auth().oauth2(outsider.token()).when().get("/api/groups/{id}", groupId)
                .then().statusCode(403).body("error.code", is("FORBIDDEN"));
    }

    @Test
    void missingAndInvalidBearerTokensReceive401() {
        given().when().get("/api/groups/{id}", groupId).then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        given().auth().oauth2("invalid").when().get("/api/groups/{id}", groupId)
                .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
    }

    @Test
    void unknownGroupReturns404() {
        given().auth().oauth2(members.getFirst().token()).when().get("/api/groups/{id}", UUID.randomUUID())
                .then().statusCode(404).body("error.code", is("NOT_FOUND"));
    }

    @Test
    void deletedMemberIsATombstoneAndTheirTokenNoLongerWorks() {
        Identity deleted = members.get(1);
        given().auth().oauth2(deleted.token()).when().delete("/api/me").then().statusCode(204);
        JsonPath response = read(members.getFirst());
        List<Map<String, Object>> visible = response.getList("members");
        Map<String, Object> tombstone = visible.stream().filter(m -> deleted.id().equals(m.get("id"))).findFirst().orElseThrow();
        assertEquals(Map.of("id", deleted.id(), "displayName", "Former participant", "isMe", false,
                "isMyFriend", false, "interests", List.of()), tombstone);
        assertEquals(4, visible.size());
        assertFalse(response.prettify().contains("Sam"));
        given().auth().oauth2(deleted.token()).when().get("/api/groups/{id}", groupId).then().statusCode(401);
    }

    @Test
    void unlinkingRemovesFriendFlagsWithoutLosingGroupAccess() {
        given().auth().oauth2(members.getFirst().token()).when().post("/api/me/friend/unlink").then().statusCode(200);
        for (Identity member : members) {
            List<Boolean> flags = read(member).getList("members.isMyFriend");
            assertEquals(List.of(false, false, false, false), flags);
        }
    }

    @Test
    void savedSlotsRenderWithBerlinSummerAndWinterOffsetsOutsideTheCurrentCatalog() {
        LunchGroup g = groups.findById(groupId).orElseThrow();
        for (var entry : Map.of("2027-07-06T12:15", "+02:00", "2027-01-05T12:15", "+01:00").entrySet()) {
            groups.save(new LunchGroup(g.id(), g.campus(), entry.getKey(), g.location(), g.memberIds(),
                    g.sharedInterests(), g.commonLanguages(), g.whyThisGroup(), g.score(), g.createdAt(), g.cards()));
            JsonPath response = read(members.getFirst());
            assertEquals(entry.getKey() + ":00" + entry.getValue(), response.getString("startsAt"));
            assertEquals("13:00", response.getString("slot.end"));
        }
    }

    private JsonPath read(Identity identity) {
        return given().auth().oauth2(identity.token()).when().get("/api/groups/{id}", groupId)
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath();
    }

    private Identity register(String name, String category, String invite) {
        Map<String, Object> body = new HashMap<>(Map.of("displayName", name, "category", category,
                "campus", "WUERZBURG", "languages", List.of("en"), "interests", List.of("travel"),
                "slotIds", List.of(slots.slots().getFirst().id()), "consent", true));
        if (invite != null) body.put("inviteCode", invite);
        JsonPath response = given().contentType(ContentType.JSON).body(body).when().post("/api/participants")
                .then().statusCode(201).extract().jsonPath();
        return new Identity(response.getString("me.id"), response.getString("accessToken"));
    }

    private record Identity(String id, String token) {}
}
