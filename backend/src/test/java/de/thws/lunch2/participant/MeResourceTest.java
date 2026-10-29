package de.thws.lunch2.participant;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.thws.lunch2.auth.TokenService;
import de.thws.lunch2.common.SlotService;
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
import de.thws.lunch2.persistence.ConnectionRepository;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.InviteRepository;
import de.thws.lunch2.persistence.JsonFileStore;
import de.thws.lunch2.persistence.JsonParticipantRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(MeResourceTest.Profile.class)
class MeResourceTest {
    @Inject
    ParticipantRepository participants;
    @Inject
    FriendPairRepository pairs;
    @Inject
    InviteRepository invites;
    @Inject
    GroupRepository groups;
    @Inject
    ConnectionRepository connections;
    @Inject
    DataAdmin admin;
    @Inject
    TokenService tokens;
    @Inject
    SlotService slots;
    @Inject
    ObjectMapper mapper;
    @Inject
    @ConfigProperty(name = "lunch2.data-file")
    String dataFile;
    @Inject
    @ConfigProperty(name = "lunch2.public-base-url")
    String publicBaseUrl;

    @BeforeEach
    @AfterEach
    void clearIsolatedStore() {
        admin.wipeAll();
    }

    @Test
    void registeredParticipantCanReadExactMeResponseWithoutCredentialOrInternalFields() {
        var registration = given().contentType(ContentType.JSON).body(Map.of(
                        "displayName", "Lena", "category", "DOMESTIC", "campus", "WUERZBURG",
                        "languages", List.of("de", "en"), "interests", List.of("travel"),
                        "slotIds", List.of(slots.slots().getFirst().id()), "consent", true))
                .when().post("/api/participants").then().statusCode(201).extract().jsonPath();

        JsonPath response = read(registration.getString("accessToken"));

        assertEquals(registration.getMap("me"), response.getMap(""));
        assertEquals(Set.of("id", "displayName", "category", "campus", "languages", "interests", "slotIds",
                "studyField", "matchStatus", "waitingReason", "groupId", "friend", "pendingInvite"),
                response.getMap("").keySet());
        assertEquals("WAITING", response.getString("matchStatus"));
        assertEquals(Map.of("code", "WAITING_FOR_NEXT_ROUND", "message", "Matching has not run since you registered."),
                response.getMap("waitingReason"));
        for (String field : List.of("studyField", "groupId", "friend", "pendingInvite")) {
            assertTrue(response.getMap("").containsKey(field));
            assertNull(response.get(field));
        }
    }

    @Test
    void bothEndpointsRequireAValidBearerToken() {
        for (String method : List.of("GET", "DELETE")) {
            given().when().request(method, "/api/me").then().statusCode(401).contentType(ContentType.JSON)
                    .body("error.code", is("UNAUTHORIZED")).body("error.details", is(List.of()));
            given().auth().oauth2(tokens.newToken()).when().request(method, "/api/me")
                    .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        }
    }

    @Test
    void matchedAndWaitingProfilesExposeOnlyTheirApplicableStatusFields() {
        Identity identity = create("Test");
        Participant waiting = state(identity.participant(), MatchStatus.WAITING, "stale-group",
                ReasonCode.NOT_ENOUGH_INTERNATIONAL, null);
        participants.save(waiting);
        JsonPath response = read(identity.token());
        assertNull(response.get("groupId"));
        assertEquals(Map.of("code", "NOT_ENOUGH_INTERNATIONAL",
                "message", "Not enough international students share your campus & time slots yet."),
                response.getMap("waitingReason"));

        participants.save(state(waiting, MatchStatus.MATCHED, "group", ReasonCode.NOT_ENOUGH_INTERNATIONAL, null));
        response = read(identity.token());
        assertEquals("group", response.getString("groupId"));
        assertTrue(response.getMap("").containsKey("waitingReason"));
        assertNull(response.get("waitingReason"));
    }

    @Test
    void friendResponseShowsOnlyAllowedFieldsForEitherSideAndHidesUnlinkedPairs() {
        Identity first = create("Lena");
        Identity second = create("Sam");
        String pairId = UUID.randomUUID().toString();
        participants.save(state(first.participant(), MatchStatus.WAITING, null, ReasonCode.WAITING_FOR_NEXT_ROUND, pairId));
        participants.save(state(second.participant(), MatchStatus.WAITING, null, ReasonCode.WAITING_FOR_NEXT_ROUND, pairId));
        pairs.save(new FriendPair(pairId, first.id(), second.id(), PairStatus.ACTIVE, null));

        var friend = read(first.token()).getMap("friend");
        assertEquals(Set.of("displayName", "pairStatus", "issue"), friend.keySet());
        assertEquals("Sam", friend.get("displayName"));
        assertEquals("ACTIVE", friend.get("pairStatus"));
        assertNull(friend.get("issue"));
        assertEquals("Lena", read(second.token()).getString("friend.displayName"));

        pairs.save(new FriendPair(pairId, first.id(), second.id(), PairStatus.INCOMPATIBLE,
                ReasonCode.FRIEND_NO_COMMON_SLOT));
        assertEquals(Map.of("code", "FRIEND_NO_COMMON_SLOT", "message", "You and your friend have no lunch slot in common."),
                read(first.token()).getMap("friend.issue"));
        assertEquals("INCOMPATIBLE", read(first.token()).getString("friend.pairStatus"));

        pairs.save(new FriendPair(pairId, first.id(), second.id(), PairStatus.UNLINKED, null));
        assertNull(read(first.token()).get("friend"));
        assertNull(read(second.token()).get("friend"));
    }

    @Test
    void missingPairOrMissingFriendRendersNull() {
        Identity first = create("Lena");
        Identity second = create("Sam");
        String pairId = UUID.randomUUID().toString();
        participants.save(state(first.participant(), MatchStatus.WAITING, null, ReasonCode.WAITING_FOR_NEXT_ROUND, pairId));
        assertNull(read(first.token()).get("friend"));
        pairs.save(new FriendPair(pairId, first.id(), second.id(), PairStatus.ACTIVE, null));
        participants.delete(second.id());
        assertNull(read(first.token()).get("friend"));
    }

    @Test
    void pendingInviteContainsOnlyOwnUnusedCodeAndConfiguredUrl() {
        Identity first = create("Lena");
        Identity second = create("Sam");
        invites.save(new Invite("USED", first.id(), Instant.now(), second.id()));
        invites.save(new Invite("OTHER", second.id(), Instant.now(), null));
        assertNull(read(first.token()).get("pendingInvite"));
        Invite unused = new Invite("K7Q2M9XA3P", first.id(), Instant.now(), null);
        invites.save(unused);
        assertEquals(Map.of("code", unused.code(), "url", publicBaseUrl.replaceAll("/+$", "") + "/#/join/" + unused.code()),
                read(first.token()).getMap("pendingInvite"));
        invites.save(new Invite(unused.code(), first.id(), unused.createdAt(), second.id()));
        assertNull(read(first.token()).get("pendingInvite"));
    }

    @Test
    void deletingUnpairedParticipantReturnsEmpty204AndRevokesTokenAcrossRestart() {
        Identity first = create("Test");
        Identity second = create("Other");

        given().auth().oauth2(first.token()).when().delete("/api/me")
                .then().statusCode(204).body(is(""));

        assertTrue(participants.findById(first.id()).isEmpty());
        assertTrue(participants.findByTokenHash(first.participant().tokenHash()).isEmpty());
        assertEquals(second.id(), read(second.token()).getString("id"));
        for (String method : List.of("GET", "DELETE")) {
            given().auth().oauth2(first.token()).when().request(method, "/api/me")
                    .then().statusCode(401).body("error.code", is("UNAUTHORIZED"));
        }
        var restarted = new JsonParticipantRepository(new JsonFileStore(mapper, dataFile));
        assertTrue(restarted.findById(first.id()).isEmpty());
        assertTrue(restarted.findByTokenHash(first.participant().tokenHash()).isEmpty());
        assertEquals(second.participant(), restarted.findById(second.id()).orElseThrow());
    }

    @Test
    void deletionCascadesInvitesPairsAndBothConnectionDirectionsWhileKeepingGroupTombstoneIds() throws IOException {
        Identity erased = create("Erase Me");
        Identity friend = create("Sam");
        Identity third = create("Aiko");
        Identity fourth = create("Omar");
        String pairId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        participants.save(state(erased.participant(), MatchStatus.MATCHED, groupId, null, pairId));
        Participant matchedFriend = state(friend.participant(), MatchStatus.MATCHED, groupId, null, pairId);
        participants.save(matchedFriend);
        pairs.save(new FriendPair(pairId, erased.id(), friend.id(), PairStatus.ACTIVE, null));
        String historicalPairId = UUID.randomUUID().toString();
        pairs.save(new FriendPair(historicalPairId, third.id(), erased.id(), PairStatus.UNLINKED, null));
        String otherPairId = UUID.randomUUID().toString();
        FriendPair otherPair = new FriendPair(otherPairId, third.id(), fourth.id(), PairStatus.ACTIVE, null);
        pairs.save(otherPair);
        participants.save(state(third.participant(), MatchStatus.MATCHED, groupId, null, otherPairId));
        invites.save(new Invite("UNUSED", erased.id(), Instant.now(), null));
        invites.save(new Invite("USED", erased.id(), Instant.now(), friend.id()));
        Invite otherInvite = new Invite("KEEP", friend.id(), Instant.now(), null);
        invites.save(otherInvite);
        CardSet cards = new CardSet("en", CardSource.FALLBACK, List.of(new Card("c1", "Favourite place?", "travel")));
        LunchGroup group = new LunchGroup(groupId, Campus.WUERZBURG, slots.slots().getFirst().id(), "Mensa",
                List.of(erased.id(), friend.id(), third.id(), fourth.id()).stream().sorted().toList(),
                List.of("travel"), List.of("en"), List.of("Everyone speaks English", "Erase Me & Sam signed up together"),
                17, Instant.now(), cards);
        groups.save(group);
        String unrelatedGroupId = UUID.randomUUID().toString();
        LunchGroup unrelatedGroup = new LunchGroup(unrelatedGroupId, group.campus(), group.slotId(), group.location(),
                List.of(friend.id(), third.id(), fourth.id(), "former-id"), List.of(), List.of("en"),
                List.of("Everyone speaks English"), 0, Instant.now(), null);
        groups.save(unrelatedGroup);
        connections.save(new ConnectionChoice(groupId, erased.id(), friend.id(), ConnectChoice.YES, "erase-contact"));
        connections.save(new ConnectionChoice(groupId, friend.id(), erased.id(), ConnectChoice.YES, "erase-contact"));
        connections.save(new ConnectionChoice(unrelatedGroupId, third.id(), erased.id(), ConnectChoice.YES, "erase-contact"));
        ConnectionChoice keep = new ConnectionChoice(groupId, friend.id(), third.id(), ConnectChoice.YES, "keep-contact");
        connections.save(keep);
        assertEquals("Erase Me", read(friend.token()).getString("friend.displayName"));

        given().auth().oauth2(erased.token()).when().delete("/api/me").then().statusCode(204);

        assertTrue(participants.findById(erased.id()).isEmpty());
        assertTrue(pairs.findById(pairId).isEmpty());
        assertTrue(pairs.findById(historicalPairId).isEmpty());
        assertEquals(otherPair, pairs.findById(otherPairId).orElseThrow());
        assertEquals(otherPairId, participants.findById(third.id()).orElseThrow().friendPairId());
        Participant survivingFriend = participants.findById(friend.id()).orElseThrow();
        assertNull(survivingFriend.friendPairId());
        assertEquals(matchedFriend.tokenHash(), survivingFriend.tokenHash());
        assertEquals(MatchStatus.MATCHED, survivingFriend.matchStatus());
        assertEquals(groupId, survivingFriend.groupId());
        assertNull(read(friend.token()).get("friend"));
        assertTrue(invites.findByCode("UNUSED").isEmpty());
        assertTrue(invites.findByCode("USED").isEmpty());
        assertEquals(otherInvite, invites.findByCode("KEEP").orElseThrow());
        assertEquals(List.of(keep), connections.findByGroup(groupId));
        assertTrue(connections.findByGroup(unrelatedGroupId).isEmpty());
        LunchGroup tombstoneGroup = groups.findById(groupId).orElseThrow();
        assertEquals(group.memberIds(), tombstoneGroup.memberIds());
        assertEquals(4, tombstoneGroup.memberIds().size());
        assertEquals(List.of("Everyone speaks English"), tombstoneGroup.whyThisGroup());
        assertEquals(cards, tombstoneGroup.cards());
        assertEquals(unrelatedGroup, groups.findById(unrelatedGroupId).orElseThrow());
        String snapshot = Files.readString(Path.of(dataFile));
        assertFalse(snapshot.contains("Erase Me"));
        assertFalse(snapshot.contains("erase-contact"));
        assertFalse(snapshot.contains(erased.participant().tokenHash()));
        given().auth().oauth2(erased.token()).when().get("/api/me").then().statusCode(401);
    }

    @Test
    void deletingInviteeUnlinksWaitingInviterAndClearsObsoleteFriendReason() {
        Identity inviter = create("Lena");
        Identity invitee = create("Sam");
        String pairId = UUID.randomUUID().toString();
        participants.save(state(inviter.participant(), MatchStatus.WAITING, null, ReasonCode.FRIEND_DIFFERENT_CAMPUS, pairId));
        participants.save(state(invitee.participant(), MatchStatus.WAITING, null, ReasonCode.FRIEND_DIFFERENT_CAMPUS, pairId));
        pairs.save(new FriendPair(pairId, inviter.id(), invitee.id(), PairStatus.INCOMPATIBLE,
                ReasonCode.FRIEND_DIFFERENT_CAMPUS));

        given().auth().oauth2(invitee.token()).when().delete("/api/me").then().statusCode(204);

        JsonPath survivor = read(inviter.token());
        assertNull(survivor.get("friend"));
        assertEquals("WAITING", survivor.getString("matchStatus"));
        assertEquals("WAITING_FOR_NEXT_ROUND", survivor.getString("waitingReason.code"));
        assertTrue(pairs.findById(pairId).isEmpty());
    }

    private JsonPath read(String token) {
        return given().auth().oauth2(token).when().get("/api/me")
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath();
    }

    private Identity create(String name) {
        String token = tokens.newToken();
        Instant now = Instant.now();
        Participant participant = new Participant(UUID.randomUUID().toString(), tokens.hash(token), name,
                Category.DOMESTIC, Campus.WUERZBURG, List.of("en"), List.of("travel"),
                List.of(slots.slots().getFirst().id()), null, MatchStatus.WAITING, null,
                ReasonCode.WAITING_FOR_NEXT_ROUND, null, now, now);
        participants.save(participant);
        return new Identity(token, participant);
    }

    private static Participant state(Participant participant, MatchStatus status, String groupId,
                                     ReasonCode waitingReason, String pairId) {
        return new Participant(participant.id(), participant.tokenHash(), participant.displayName(), participant.category(),
                participant.campus(), participant.languages(), participant.interests(), participant.slotIds(),
                participant.studyField(), status, groupId, waitingReason, pairId, participant.createdAt(), participant.consentAt());
    }

    private record Identity(String token, Participant participant) {
        String id() {
            return participant.id();
        }
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("lunch2.data-file", "target/test-data/hack106.json");
        }
    }
}
