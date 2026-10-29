package de.thws.lunch2.invite;

import de.thws.lunch2.common.SlotService;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.InviteRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(InviteResourceTest.Profile.class)
class InviteResourceTest {
    @Inject
    DataAdmin admin;
    @Inject
    ParticipantRepository participants;
    @Inject
    FriendPairRepository pairs;
    @Inject
    InviteRepository invites;
    @Inject
    SlotService slots;
    @Inject
    @ConfigProperty(name = "lunch2.public-base-url")
    String publicBaseUrl;

    @BeforeEach
    @AfterEach
    void clearIsolatedStore() {
        admin.wipeAll();
    }

    @Test
    void createIsIdempotentAndPreviewIsPublicWithOnlyAllowedFields() {
        Identity first = register("Lena", Map.of());
        JsonPath invite = invite(first);
        String code = invite.getString("code");
        assertTrue(code.matches("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{10}"));
        assertEquals(Set.of("code", "url"), invite.getMap("").keySet());
        assertEquals(publicBaseUrl.replaceAll("/+$", "") + "/#/join/" + code, invite.getString("url"));
        assertEquals(invite.getMap(""), invite(first).getMap(""));
        assertEquals(invite.getMap(""), me(first).getMap("pendingInvite"));
        var preview = given().when().get("/api/invites/{code}", code)
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath();
        assertEquals(Map.of("code", code, "inviterDisplayName", "Lena", "campus", "WUERZBURG", "valid", true,
                        "mensaId", me(first).getString("mensaId"), "slotIds", me(first).getList("slotIds"),
                        "inviterMatchStatus", "WAITING"),
                preview.getMap(""));
        assertEquals(first.id(), invites.findByCode(code).orElseThrow().inviterId());
    }

    @Test
    void previewUsesExplicitMensaAndSlotsForBuddyRegistration() {
        var catalog = given().when().get("/api/meta").then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> mensas = catalog.getList("mensas");
        var mensa = mensas.stream().filter(m -> "WUERZBURG".equals(m.get("campus")))
                .filter(m -> !Boolean.TRUE.equals(m.get("defaultForCampus")) && !((List<?>) m.get("slotIds")).isEmpty())
                .findFirst().orElseThrow();
        String mensaId = (String) mensa.get("id");
        List<?> chosen = ((List<?>) mensa.get("slotIds")).subList(0, 1);
        Identity first = register("Lena", Map.of("mensaId", mensaId, "slotIds", chosen));
        String code = invite(first).getString("code");
        var preview = given().when().get("/api/invites/{code}", code).then().statusCode(200)
                .body("mensaId", is(mensaId)).body("slotIds", is(chosen)).extract().jsonPath();
        Identity buddy = register("Buddy", Map.of("inviteCode", code, "campus", preview.getString("campus"),
                "mensaId", preview.getString("mensaId"), "slotIds", preview.getList("slotIds")));
        assertEquals("ACTIVE", me(buddy).getString("friend.pairStatus"));
        assertEquals(mensaId, me(buddy).getString("mensaId"));
    }

    @Test
    void fullRegistrationFlowLinksBothProfilesMarksInviteUsedAndRejectsSecondUse() {
        Identity first = register("Lena", Map.of());
        String code = invite(first).getString("code");
        Identity second = register("Sam", Map.of("inviteCode", code));

        assertEquals("Lena", second.registration().getString("me.friend.displayName"));
        assertEquals("Sam", me(first).getString("friend.displayName"));
        assertEquals("Lena", me(second).getString("friend.displayName"));
        assertEquals("ACTIVE", me(first).getString("friend.pairStatus"));
        assertNull(me(first).get("friend.issue"));
        assertNull(me(first).get("pendingInvite"));
        String pairId = participants.findById(first.id()).orElseThrow().friendPairId();
        assertEquals(pairId, participants.findById(second.id()).orElseThrow().friendPairId());
        assertEquals(PairStatus.ACTIVE, pairs.findById(pairId).orElseThrow().status());
        assertEquals(second.id(), invites.findByCode(code).orElseThrow().usedById());
        given().when().get("/api/invites/{code}", code).then().statusCode(200).body("valid", is(false));
        int before = participants.findAll().size();
        postRegistration("Third", Map.of("inviteCode", code)).then().statusCode(409).body("error.code", is("INVITE_USED"));
        assertEquals(before, participants.findAll().size());
        for (Identity identity : List.of(first, second)) {
            given().auth().oauth2(identity.token()).when().post("/api/me/invite")
                    .then().statusCode(409).body("error.code", is("ALREADY_PAIRED"));
        }
    }

    @Test
    void compatibilityChecksCampusThenSlotThenLanguageAndRegistrationStillSucceeds() {
        List<Map<String, Object>> changes = List.of(
                Map.of("campus", "SCHWEINFURT", "slotIds", List.of(slots.slots().get(1).id()), "languages", List.of("tr")),
                Map.of("slotIds", List.of(slots.slots().get(1).id()), "languages", List.of("tr")),
                Map.of("languages", List.of("tr")));
        List<ReasonCode> expected = List.of(ReasonCode.FRIEND_DIFFERENT_CAMPUS,
                ReasonCode.FRIEND_NO_COMMON_SLOT, ReasonCode.FRIEND_NO_COMMON_LANGUAGE);
        for (int index = 0; index < changes.size(); index++) {
            Identity first = register("Lena", Map.of());
            String code = invite(first).getString("code");
            Map<String, Object> options = new LinkedHashMap<>(changes.get(index));
            options.put("inviteCode", code);
            Identity second = register("Sam", options);
            assertEquals("INCOMPATIBLE", second.registration().getString("me.friend.pairStatus"));
            assertEquals(expected.get(index).name(), me(first).getString("friend.issue.code"));
            assertEquals(expected.get(index).name(), me(second).getString("friend.issue.code"));
            assertEquals("WAITING", me(second).getString("matchStatus"));
            assertEquals(second.id(), invites.findByCode(code).orElseThrow().usedById());
            given().auth().oauth2(first.token()).when().post("/api/me/invite")
                    .then().statusCode(409).body("error.code", is("ALREADY_PAIRED"));
        }
    }

    @Test
    void overlapOfAnyLanguageAndAnySlotMakesPairActive() {
        Identity first = register("Lena", Map.of("languages", List.of("de", "en"),
                "slotIds", List.of(slots.slots().getFirst().id(), slots.slots().get(1).id())));
        Identity second = register("Sam", Map.of("inviteCode", invite(first).getString("code"),
                "languages", List.of("tr", "en"), "slotIds", List.of(slots.slots().get(1).id())));
        assertEquals("ACTIVE", me(second).getString("friend.pairStatus"));
    }

    @Test
    void alreadyMatchedInviterTakesPrecedenceAndKeepsExistingGroup() {
        Identity first = register("Lena", Map.of());
        String code = invite(first).getString("code");
        Participant participant = participants.findById(first.id()).orElseThrow();
        participants.save(state(participant, MatchStatus.MATCHED, "existing-group", null));
        given().when().get("/api/invites/{code}", code).then().statusCode(200)
                .body("valid", is(true)).body("inviterMatchStatus", is("MATCHED"));
        Identity second = register("Sam", Map.of("inviteCode", code, "campus", "SCHWEINFURT",
                "languages", List.of("tr"), "slotIds", List.of(slots.slots().get(1).id())));
        assertEquals("INVITER_ALREADY_MATCHED", me(second).getString("friend.issue.code"));
        assertEquals("INCOMPATIBLE", me(second).getString("friend.pairStatus"));
        assertEquals("MATCHED", me(first).getString("matchStatus"));
        assertEquals("existing-group", me(first).getString("groupId"));
        assertEquals("WAITING", me(second).getString("matchStatus"));
    }

    @Test
    void eitherMemberCanUnlinkAndInviteAgainWithoutReusingTheOldCode() {
        Identity first = register("Lena", Map.of());
        String code = invite(first).getString("code");
        Identity second = register("Sam", Map.of("inviteCode", code, "campus", "SCHWEINFURT"));
        String pairId = participants.findById(first.id()).orElseThrow().friendPairId();
        Participant waiting = participants.findById(first.id()).orElseThrow();
        participants.save(state(waiting, MatchStatus.WAITING, null, ReasonCode.FRIEND_DIFFERENT_CAMPUS));

        given().auth().oauth2(second.token()).when().post("/api/me/friend/unlink")
                .then().statusCode(200).body("friend", is((Object) null));

        assertEquals(PairStatus.UNLINKED, pairs.findById(pairId).orElseThrow().status());
        assertNull(pairs.findById(pairId).orElseThrow().issue());
        for (Identity identity : List.of(first, second)) {
            assertNull(me(identity).get("friend"));
            assertNull(participants.findById(identity.id()).orElseThrow().friendPairId());
        }
        assertEquals("WAITING_FOR_NEXT_ROUND", me(first).getString("waitingReason.code"));
        given().auth().oauth2(first.token()).when().post("/api/me/friend/unlink").then().statusCode(200);
        String newCode = invite(first).getString("code");
        assertTrue(!newCode.equals(code));
        Identity third = register("Aiko", Map.of("inviteCode", newCode));
        assertEquals("Aiko", me(first).getString("friend.displayName"));
        assertEquals("Lena", me(third).getString("friend.displayName"));
        assertNull(me(second).get("friend"));
        postRegistration("Omar", Map.of("inviteCode", code)).then().statusCode(409).body("error.code", is("INVITE_USED"));
    }

    @Test
    void unlinkPreservesExistingMatchAssignments() {
        Identity first = register("Lena", Map.of());
        Identity second = register("Sam", Map.of("inviteCode", invite(first).getString("code")));
        for (Identity identity : List.of(first, second)) {
            participants.save(state(participants.findById(identity.id()).orElseThrow(), MatchStatus.MATCHED, "group", null));
        }
        given().auth().oauth2(first.token()).when().post("/api/me/friend/unlink").then().statusCode(200);
        for (Identity identity : List.of(first, second)) {
            assertEquals("MATCHED", me(identity).getString("matchStatus"));
            assertEquals("group", me(identity).getString("groupId"));
            assertNull(me(identity).get("friend"));
        }
    }

    @Test
    void invalidCodesAndDeletedInviterAreUnavailableWithoutCreatingParticipants() {
        for (String code : List.of("UNKNOWN", "", "   ")) {
            postRegistration("Sam", Map.of("inviteCode", code)).then().statusCode(404).contentType(ContentType.JSON)
                    .body("error.code", is("INVITE_INVALID")).body("error.details", is(List.of()));
        }
        assertTrue(participants.findAll().isEmpty());
        assertTrue(pairs.findAll().isEmpty());
        given().when().get("/api/invites/UNKNOWN").then().statusCode(404).body("error.code", is("INVITE_INVALID"));
        Identity first = register("Lena", Map.of());
        String code = invite(first).getString("code");
        given().auth().oauth2(first.token()).when().delete("/api/me").then().statusCode(204);
        given().when().get("/api/invites/{code}", code).then().statusCode(404).body("error.code", is("INVITE_INVALID"));
        postRegistration("Sam", Map.of("inviteCode", code)).then().statusCode(404).body("error.code", is("INVITE_INVALID"));
        assertTrue(participants.findAll().isEmpty());
    }

    @Test
    void creationAndUnlinkRequireAuthentication() {
        for (String endpoint : List.of("/api/me/invite", "/api/me/friend/unlink")) {
            given().when().post(endpoint).then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
            given().header("Authorization", "Bearer invalid").when().post(endpoint)
                    .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        }
    }

    @Test
    void simultaneousInviteCreationReturnsTheSameCode() throws Exception {
        Identity first = register("Lena", Map.of());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> invite(first).getString("code"));
            var b = executor.submit(() -> invite(first).getString("code"));
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void simultaneousRedemptionAllowsExactlyOneNewParticipantAndPair() throws Exception {
        Identity first = register("Lena", Map.of());
        String code = invite(first).getString("code");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> postRegistration("Sam", Map.of("inviteCode", code)));
            var b = executor.submit(() -> postRegistration("Aiko", Map.of("inviteCode", code)));
            List<Response> results = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), results.stream().map(Response::statusCode).sorted().toList());
            assertEquals("INVITE_USED", results.stream().filter(result -> result.statusCode() == 409)
                    .findFirst().orElseThrow().jsonPath().getString("error.code"));
        }
        assertEquals(2, participants.findAll().size());
        assertEquals(1, pairs.findAll().size());
        String usedBy = invites.findByCode(code).orElseThrow().usedById();
        assertTrue(participants.findById(usedBy).isPresent());
        assertEquals(pairs.findAll().getFirst().id(), participants.findById(usedBy).orElseThrow().friendPairId());
    }

    @Test
    void realPairDeletionClearsSurvivingFriendsProfile() {
        Identity first = register("Lena", Map.of());
        Identity second = register("Sam", Map.of("inviteCode", invite(first).getString("code")));
        given().auth().oauth2(second.token()).when().delete("/api/me").then().statusCode(204);
        assertNull(me(first).get("friend"));
        assertTrue(pairs.findAll().isEmpty());
        assertTrue(invites.findUnusedByInviter(first.id()).isEmpty());
    }

    private Identity register(String name, Map<String, Object> overrides) {
        JsonPath result = postRegistration(name, overrides).then().statusCode(201).extract().jsonPath();
        return new Identity(result.getString("me.id"), result.getString("accessToken"), result);
    }

    private Response postRegistration(String name, Map<String, Object> overrides) {
        Map<String, Object> request = new LinkedHashMap<>(Map.of("displayName", name, "category", "DOMESTIC",
                "campus", "WUERZBURG", "languages", List.of("de", "en"), "interests", List.of("travel"),
                "slotIds", List.of(slots.slots().getFirst().id()), "consent", true));
        request.putAll(overrides);
        return given().contentType(ContentType.JSON).body(request).when().post("/api/participants");
    }

    private JsonPath invite(Identity identity) {
        return given().auth().oauth2(identity.token()).when().post("/api/me/invite")
                .then().statusCode(201).contentType(ContentType.JSON).extract().jsonPath();
    }

    private JsonPath me(Identity identity) {
        return given().auth().oauth2(identity.token()).when().get("/api/me")
                .then().statusCode(200).extract().jsonPath();
    }

    private static Participant state(Participant participant, MatchStatus status, String groupId, ReasonCode reason) {
        return new Participant(participant.id(), participant.tokenHash(), participant.displayName(), participant.category(),
                participant.campus(), participant.languages(), participant.interests(), participant.slotIds(),
                participant.studyField(), status, groupId, reason, participant.friendPairId(),
                participant.createdAt(), participant.consentAt());
    }

    private record Identity(String id, String token, JsonPath registration) {}

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("lunch2.data-file", "target/test-data/hack105.json");
        }
    }
}
