package de.thws.lunch2.matching;

import de.thws.lunch2.domain.*;
import de.thws.lunch2.persistence.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MensaMatchingTest {
    @Inject DataAdmin data;
    @Inject ParticipantRepository participants;
    @Inject FriendPairRepository pairs;
    @BeforeEach void reset() { data.wipeAll(); }
    String signup(String name, String category, String mensa) {
        return given().contentType("application/json").body(Map.of("displayName", name, "category", category,
                "campus", "WUERZBURG", "languages", List.of("en"), "interests", List.of("travel"),
                "slotIds", List.of("2026-11-02T11:30"), "consent", true, "mensaId", mensa))
                .post("/api/participants").then().statusCode(201).extract().path("accessToken");
    }
    @Test void matchesOnlyWithinMensaAndRevealsExactLocationToMembers() {
        String a = signup("One", "DOMESTIC", "WUERZBURG_JOSEF_SCHNEIDER");
        signup("Two", "DOMESTIC", "WUERZBURG_JOSEF_SCHNEIDER");
        String outsider = signup("Three", "INTERNATIONAL", "WUERZBURG_ROENTGENRING");
        signup("Four", "INTERNATIONAL", "WUERZBURG_ROENTGENRING");
        given().header("X-Admin-Key", "dev-admin-key").post("/api/admin/match").then().statusCode(200).body("groupsCreated.size()", is(0));
        signup("Five", "INTERNATIONAL", "WUERZBURG_JOSEF_SCHNEIDER");
        signup("Six", "INTERNATIONAL", "WUERZBURG_JOSEF_SCHNEIDER");
        String group = given().header("X-Admin-Key", "dev-admin-key").post("/api/admin/match").then().statusCode(200)
                .body("groupsCreated.size()", is(1)).extract().path("groupsCreated[0].groupId");
        given().auth().oauth2(a).get("/api/me").then().statusCode(200).body("matchStatus", is("MATCHED")).body("groupId", is(group));
        var response = given().auth().oauth2(a).get("/api/groups/" + group).then().statusCode(200)
                .body("location", containsString("Josef-Schneider-Straße 9")).body("members.size()", is(4))
                .body("startsAt", is("2026-11-02T11:30:00+01:00")).extract().jsonPath();
        for (Map<String, Object> member : response.<Map<String, Object>>getList("members")) {
            assertFalse(member.containsKey("category")); assertFalse(member.containsKey("languages"));
            assertFalse(member.containsKey("mensaId")); assertFalse(member.containsKey("tokenHash"));
        }
        given().auth().oauth2(outsider).get("/api/groups/" + group).then().statusCode(403);
        given().header("X-Admin-Key", "dev-admin-key").post("/api/admin/match").then().statusCode(200).body("groupsCreated.size()", is(0));
        given().auth().oauth2(a).delete("/api/me").then().statusCode(204);
    }
    @Test void neverSplitsFriendPairAcrossMensas() {
        signup("One", "DOMESTIC", "WUERZBURG_JOSEF_SCHNEIDER");
        signup("Two", "DOMESTIC", "WUERZBURG_ROENTGENRING");
        var people = participants.findAll();
        var a = people.get(0); var b = people.get(1);
        pairs.save(new FriendPair("pair", a.id(), b.id(), PairStatus.ACTIVE, null));
        participants.save(a.withFriendPair("pair")); participants.save(b.withFriendPair("pair"));
        given().header("X-Admin-Key", "dev-admin-key").post("/api/admin/match").then().statusCode(200)
                .body("groupsCreated.size()", is(0)).body("waiting.reasonCode", everyItem(is("FRIEND_DIFFERENT_MENSA")));
        assertTrue(participants.findAll().stream().allMatch(p -> p.matchStatus() == MatchStatus.WAITING));
    }
    @Test void incomingInviteAndUnlinkFlowsPreserveSelectedMensa() {
        String token = signup("One", "DOMESTIC", "WUERZBURG_JOSEF_SCHNEIDER");
        String code = given().auth().oauth2(token).post("/api/me/invite").then().statusCode(201).extract().path("code");
        String friend = given().contentType("application/json").body(Map.of("displayName", "Friend", "category", "INTERNATIONAL",
                "campus", "WUERZBURG", "languages", List.of("en"), "interests", List.of("travel"),
                "slotIds", List.of("2026-11-02T11:30"), "consent", true, "mensaId", "WUERZBURG_JOSEF_SCHNEIDER", "inviteCode", code))
                .post("/api/participants").then().statusCode(201).body("me.friend.pairStatus", is("ACTIVE"))
                .body("me.mensaId", is("WUERZBURG_JOSEF_SCHNEIDER")).extract().path("accessToken");
        given().auth().oauth2(friend).post("/api/me/friend/unlink").then().statusCode(200).body("mensaId", is("WUERZBURG_JOSEF_SCHNEIDER"));
        given().auth().oauth2(token).get("/api/me").then().statusCode(200).body("mensaId", is("WUERZBURG_JOSEF_SCHNEIDER"));
    }

    @Test void incomingInviteReportsDifferentMensasAndDeletionKeepsFriendsMensa() {
        String token = signup("One", "DOMESTIC", "WUERZBURG_JOSEF_SCHNEIDER");
        String code = given().auth().oauth2(token).post("/api/me/invite").then().statusCode(201).extract().path("code");
        String friend = given().contentType("application/json").body(Map.of("displayName", "Friend", "category", "INTERNATIONAL",
                "campus", "WUERZBURG", "languages", List.of("en"), "interests", List.of("travel"),
                "slotIds", List.of("2026-11-02T11:30"), "consent", true, "mensaId", "WUERZBURG_ROENTGENRING", "inviteCode", code))
                .post("/api/participants").then().statusCode(201).body("me.friend.issue.code", is("FRIEND_DIFFERENT_MENSA"))
                .extract().path("accessToken");
        given().header("X-Admin-Key", "dev-admin-key").post("/api/admin/match").then().statusCode(200)
                .body("groupsCreated.size()", is(0)).body("waiting.reasonCode", everyItem(is("FRIEND_DIFFERENT_MENSA")));
        given().auth().oauth2(friend).delete("/api/me").then().statusCode(204);
        given().auth().oauth2(token).get("/api/me").then().statusCode(200).body("mensaId", is("WUERZBURG_JOSEF_SCHNEIDER"))
                .body("friend", nullValue()).body("waitingReason.code", is("WAITING_FOR_NEXT_ROUND"));
    }

    @Test void protectsMatchingWithAdminKey() {
        given().post("/api/admin/match").then().statusCode(401);
    }
}
