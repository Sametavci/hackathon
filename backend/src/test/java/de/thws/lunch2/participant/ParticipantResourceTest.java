package de.thws.lunch2.participant;

import de.thws.lunch2.auth.TokenService;
import de.thws.lunch2.common.SlotService;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ParticipantResourceTest {
    @Inject
    ParticipantRepository participants;
    @Inject
    TokenService tokens;
    @Inject
    SlotService slots;
    @Inject
    @ConfigProperty(name = "lunch2.public-base-url")
    String publicBaseUrl;

    private final List<String> createdIds = new ArrayList<>();

    @AfterEach
    void deleteFixtures() {
        createdIds.forEach(participants::delete);
    }

    @Test
    void registrationReturns201FullMeAndRestoreLinkAndStoresOnlyHashWithConsentTime() {
        Instant before = Instant.now();
        var response = register(payload()).then().statusCode(201).contentType(ContentType.JSON).extract().jsonPath();
        String id = response.getString("me.id");
        String token = response.getString("accessToken");
        assertEquals(4, UUID.fromString(id).version());
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(publicBaseUrl.replaceAll("/+$", "") + "/#/back?t=" + token, response.getString("restoreUrl"));
        assertEquals(Set.of("accessToken", "restoreUrl", "me"), response.getMap("").keySet());
        Map<String, Object> me = response.getMap("me");
        assertEquals(Set.of("id", "displayName", "category", "campus", "languages", "interests", "slotIds", "studyField",
                "matchStatus", "waitingReason", "groupId", "friend", "pendingInvite", "mensaId"), me.keySet());
        assertEquals("Lena", me.get("displayName"));
        assertEquals("DOMESTIC", me.get("category"));
        assertEquals("WUERZBURG", me.get("campus"));
        assertEquals("COMPUTER_SCIENCE", me.get("studyField"));
        assertEquals(List.of("de", "en"), me.get("languages"));
        assertEquals(List.of("travel", "gaming", "cooking"), me.get("interests"));
        assertEquals("WAITING", me.get("matchStatus"));
        assertEquals(Map.of("code", "WAITING_FOR_NEXT_ROUND", "message", "Matching has not run since you registered."),
                me.get("waitingReason"));
        for (String nullable : List.of("groupId", "friend", "pendingInvite")) {
            assertTrue(me.containsKey(nullable));
            assertEquals(null, me.get(nullable));
        }
        var saved = participants.findById(id).orElseThrow();
        assertEquals(tokens.hash(token), saved.tokenHash());
        assertFalse(saved.tokenHash().equals(token));
        assertEquals(MatchStatus.WAITING, saved.matchStatus());
        assertEquals(ReasonCode.WAITING_FOR_NEXT_ROUND, saved.waitingReason());
        assertNotNull(saved.consentAt());
        assertEquals(saved.createdAt(), saved.consentAt());
        assertFalse(saved.consentAt().isBefore(before));
        assertFalse(saved.consentAt().isAfter(Instant.now()));
        given().auth().oauth2(token).when().get("/api/test/auth/participant")
                .then().statusCode(200).body("id", is(id));
    }

    @Test
    void missingFalseAndNullConsentReturnConsentRequiredWithoutSaving() {
        var missing = payload();
        missing.remove("consent");
        assertRejected(missing, "CONSENT_REQUIRED", null);
        for (Boolean consent : new Boolean[]{false, null}) {
            var request = payload();
            request.put("consent", consent);
            assertRejected(request, "CONSENT_REQUIRED", null);
        }
    }

    @Test
    void unknownSlotsReturnUnknownSlotWithoutSaving() {
        var request = payload();
        request.put("slotIds", List.of("2099-12-31T12:15"));
        assertRejected(request, "UNKNOWN_SLOT", "slotIds");
    }

    @Test
    void namesRejectEmailInvalidLengthAndSymbols() {
        for (String name : List.of("a@b.de", "a", "a".repeat(31), "   ", "Lena<script>")) {
            var request = payload();
            request.put("displayName", name);
            assertRejected(request, "VALIDATION_FAILED", "displayName");
        }
    }

    @Test
    void unicodeNameAndOmittedOptionalFieldsAreAccepted() {
        var request = payload();
        request.put("displayName", "Çağrı");
        request.remove("studyField");
        request.remove("inviteCode");
        var me = register(request).then().statusCode(201).extract().jsonPath().getMap("me");
        assertEquals("Çağrı", me.get("displayName"));
        assertTrue(me.containsKey("studyField"));
        assertEquals(null, me.get("studyField"));
    }

    @Test
    void unknownCatalogIdsReturnFieldDetailsWithoutSaving() {
        for (String field : List.of("languages", "interests", "studyField")) {
            var request = payload();
            request.put(field, field.equals("studyField") ? "UNKNOWN" : List.of("unknown"));
            assertRejected(request, "VALIDATION_FAILED", field);
        }
    }

    @Test
    void requiredFieldsAndListSizesAreValidated() {
        for (String field : List.of("displayName", "category", "campus", "languages", "interests", "slotIds")) {
            var request = payload();
            request.remove(field);
            assertRejected(request, "VALIDATION_FAILED", field);
        }
        for (String field : List.of("languages", "interests", "slotIds")) {
            var request = payload();
            request.put(field, List.of());
            assertRejected(request, "VALIDATION_FAILED", field);
        }
        for (var excessive : Map.of("languages", 5, "interests", 6, "slotIds", 16).entrySet()) {
            var request = payload();
            request.put(excessive.getKey(), java.util.Collections.nCopies(excessive.getValue(), "en"));
            assertRejected(request, "VALIDATION_FAILED", excessive.getKey());
        }
    }

    @Test
    void invalidEnumsAndJsonTypesReturnSafeValidationEnvelope() {
        for (String field : List.of("category", "campus", "languages")) {
            var request = payload();
            request.put(field, "UNKNOWN");
            assertRejected(request, "VALIDATION_FAILED", field);
        }
        int before = participants.findAll().size();
        given().contentType(ContentType.JSON).body("{invalid JSON")
                .when().post("/api/participants")
                .then().statusCode(400).contentType(ContentType.JSON)
                .body("error.code", is("VALIDATION_FAILED"))
                .body("error.message", is("Please provide a valid JSON request."));
        assertEquals(before, participants.findAll().size());
    }

    @Test
    void unknownInviteIsRejectedWithoutSaving() {
        var request = payload();
        request.put("inviteCode", "K7Q2M9XA3P");
        assertRejected(request, "INVITE_INVALID", null, 404);
    }

    private Map<String, Object> payload() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("displayName", "Lena");
        request.put("category", "DOMESTIC");
        request.put("campus", "WUERZBURG");
        request.put("languages", List.of("de", "en"));
        request.put("interests", List.of("travel", "gaming", "cooking"));
        request.put("slotIds", List.of(slots.slots().getFirst().id()));
        request.put("studyField", "COMPUTER_SCIENCE");
        request.put("inviteCode", null);
        request.put("consent", true);
        return request;
    }

    private Response register(Map<String, Object> request) {
        Response response = given().contentType(ContentType.JSON).body(request).when().post("/api/participants");
        if (response.statusCode() == 201) {
            createdIds.add(response.jsonPath().getString("me.id"));
        }
        return response;
    }

    private void assertRejected(Map<String, Object> request, String code, String field) {
        assertRejected(request, code, field, 400);
    }

    private void assertRejected(Map<String, Object> request, String code, String field, int status) {
        int before = participants.findAll().size();
        var response = register(request).then().statusCode(status).contentType(ContentType.JSON)
                .body("error.code", is(code)).extract().jsonPath();
        assertEquals(Set.of("error"), response.getMap("").keySet());
        assertEquals(Set.of("code", "message", "details"), response.getMap("error").keySet());
        if (field != null) {
            assertTrue(response.<String>getList("error.details.field").contains(field));
        }
        assertEquals(before, participants.findAll().size());
    }
}
