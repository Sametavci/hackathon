package de.thws.lunch2.cards;

import de.thws.lunch2.auth.TokenService;
import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CardsResourceTest {
    @Inject DataAdmin data;
    @Inject ParticipantRepository participants;
    @Inject GroupRepository groups;
    @Inject TokenService tokens;
    private String groupId;
    private String memberToken;
    private String outsiderToken;
    private LunchGroup original;

    @BeforeEach
    void fixtures() {
        data.wipeAll();
        groupId = UUID.randomUUID().toString();
        memberToken = tokens.newToken();
        outsiderToken = tokens.newToken();
        String memberId = UUID.randomUUID().toString();
        saveParticipant(memberId, memberToken);
        saveParticipant(UUID.randomUUID().toString(), outsiderToken);
        original = new LunchGroup(groupId, Campus.WUERZBURG, "2026-11-03T12:15", "Mensa",
                List.of(memberId, "second", "third", "fourth"), List.of("travel", "gaming"), List.of("de", "en"),
                List.of("Everyone speaks English"), 13, Instant.EPOCH, null);
        groups.save(original);
    }

    @AfterEach
    void cleanup() { data.wipeAll(); }

    @Test
    void memberGetsFiveFallbackCardsWithExactlyTheContractFields() {
        assertNull(groups.findById(groupId).orElseThrow().cards());
        JsonPath response = read();
        assertEquals(Set.of("groupId", "language", "source", "cards"), response.getMap("").keySet());
        assertEquals(groupId, response.getString("groupId"));
        assertEquals("en", response.getString("language"));
        assertEquals("FALLBACK", response.getString("source"));
        List<Map<String, String>> cards = response.getList("cards");
        assertEquals(5, cards.size());
        for (Map<String, String> card : cards) {
            assertEquals(Set.of("id", "text", "topic"), card.keySet());
            assertFalse(card.get("text").isBlank());
            assertTrue(card.get("text").length() <= 140);
        }
    }

    @Test
    void repeatedReadsReturnPersistedCacheAndPreserveOtherGroupFields() {
        JsonPath first = read();
        LunchGroup cached = groups.findById(groupId).orElseThrow();
        assertNotNull(cached.cards());
        assertEquals(first.getMap(""), read().getMap(""));
        assertEquals(cached, groups.findById(groupId).orElseThrow());
        assertEquals(new LunchGroup(original.id(), original.campus(), original.slotId(), original.location(),
                original.memberIds(), original.sharedInterests(), original.commonLanguages(), original.whyThisGroup(),
                original.score(), original.createdAt(), cached.cards()), cached);
    }

    @Test
    void nonMemberCannotReadOrGenerateCardsIncludingCachedCards() {
        forbidden();
        assertNull(groups.findById(groupId).orElseThrow().cards());
        read();
        forbidden();
    }

    @Test
    void missingOrInvalidTokenReturns401WithoutCreatingCards() {
        given().when().get("/api/groups/{id}/cards", groupId).then().statusCode(401);
        given().auth().oauth2("invalid").when().get("/api/groups/{id}/cards", groupId).then().statusCode(401);
        assertNull(groups.findById(groupId).orElseThrow().cards());
    }

    @Test
    void unknownGroupReturns404() {
        given().auth().oauth2(memberToken).when().get("/api/groups/{id}/cards", UUID.randomUUID())
                .then().statusCode(404).body("error.code", is("NOT_FOUND"));
    }

    @Test
    void germanGroupGetsGermanCards() {
        languages(List.of("de"));
        JsonPath response = read();
        assertEquals("de", response.getString("language"));
        assertEquals(FallbackCards.pick(groupId, original.sharedInterests(), "de"), groups.findById(groupId).orElseThrow().cards().cards());
    }

    @Test
    void firstCommonLanguageIsUsedWhenEnglishIsNotCommon() {
        languages(List.of("tr", "de"));
        JsonPath response = read();
        assertEquals("tr", response.getString("language"));
        assertEquals(FallbackCards.pick(groupId, original.sharedInterests(), "tr"), groups.findById(groupId).orElseThrow().cards().cards());
    }

    private JsonPath read() {
        return given().auth().oauth2(memberToken).when().get("/api/groups/{id}/cards", groupId)
                .then().statusCode(200).contentType(ContentType.JSON).extract().jsonPath();
    }

    private void forbidden() {
        given().auth().oauth2(outsiderToken).when().get("/api/groups/{id}/cards", groupId)
                .then().statusCode(403).body("error.code", is("FORBIDDEN"));
    }

    private void languages(List<String> languages) {
        groups.save(new LunchGroup(original.id(), original.campus(), original.slotId(), original.location(),
                original.memberIds(), original.sharedInterests(), languages, original.whyThisGroup(),
                original.score(), original.createdAt(), null));
    }

    private void saveParticipant(String id, String token) {
        participants.save(new Participant(id, tokens.hash(token), "Demo", Category.DOMESTIC, Campus.WUERZBURG,
                List.of("en"), List.of("travel"), List.of("2026-11-03T12:15"), null, MatchStatus.MATCHED,
                groupId, null, null, Instant.EPOCH, Instant.EPOCH));
    }
}
