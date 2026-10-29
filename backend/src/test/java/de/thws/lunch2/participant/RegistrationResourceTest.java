package de.thws.lunch2.participant;

import de.thws.lunch2.common.MensaService;
import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RegistrationResourceTest {
    @Inject DataAdmin data;
    @Inject ParticipantRepository participants;
    @Inject MensaService catalog;
    @BeforeEach void reset() { data.wipeAll(); }
    Map<String, Object> request() {
        Map<String, Object> body = new HashMap<>();
        body.put("displayName", "İpek"); body.put("category", "DOMESTIC"); body.put("campus", "WUERZBURG");
        body.put("languages", List.of("en")); body.put("interests", List.of("travel"));
        body.put("slotIds", List.of("2026-11-02T11:30")); body.put("consent", true);
        body.put("mensaId", "WUERZBURG_ROENTGENRING");
        return body;
    }
    @Test void metaServesFiveMensasWithTheirOwnValidSlots() {
        given().get("/api/meta").then().statusCode(200).body("mensas.size()", is(5)).body("languages.size()", is(12));
        for (var mensa : catalog.mensas()) for (String id : mensa.slotIds()) {
            var slot = catalog.slot(id);
            var date = java.time.LocalDate.parse(slot.date());
            var h = mensa.hours().stream().filter(hours -> hours.dayOfWeek() == date.getDayOfWeek().getValue()).findFirst().orElseThrow();
            assertTrue(slot.start().compareTo(h.lunchStart()) >= 0);
            assertTrue(slot.start().compareTo(h.foodUntil()) < 0);
            assertTrue(slot.end().compareTo(h.closes()) <= 0);
            assertEquals(45, java.time.Duration.between(java.time.LocalTime.parse(slot.start()), java.time.LocalTime.parse(slot.end())).toMinutes());
        }
        var schweinfurt = catalog.resolve(Campus.SCHWEINFURT, null);
        assertTrue(schweinfurt.slotIds().contains("2026-11-02T13:15"));
        assertTrue(schweinfurt.slotIds().contains("2026-11-06T13:15"));
        assertEquals("14:00", catalog.slot("2026-11-06T13:15").end());
        assertFalse(schweinfurt.slotIds().contains("2026-11-06T14:00"));
        assertFalse(schweinfurt.slotIds().stream().anyMatch(id -> id.endsWith("T08:00")));
    }
    @Test void registersPersistsAndReturnsMensaWithoutExposingTokenHash() {
        String token = given().contentType("application/json").body(request()).post("/api/participants")
                .then().statusCode(201).body("me.mensaId", is("WUERZBURG_ROENTGENRING"))
                .body("me.tokenHash", nullValue()).extract().path("accessToken");
        given().auth().oauth2(token).get("/api/me").then().statusCode(200)
                .body("displayName", is("İpek")).body("mensaId", is("WUERZBURG_ROENTGENRING"));
        assertEquals("WUERZBURG_ROENTGENRING", participants.findAll().getFirst().mensaId());
        assertNotEquals(token, participants.findAll().getFirst().tokenHash());
    }
    @Test void acceptsLegacyRequestsAndResolvesCampusDefault() {
        var body = request(); body.remove("mensaId"); body.put("campus", "SCHWEINFURT");
        given().contentType("application/json").body(body).post("/api/participants").then().statusCode(201)
                .body("me.mensaId", is("SCHWEINFURT_THWS"));
    }
    @Test void rejectsWrongCampusUnknownMensaAndUnavailableTimes() {
        var body = request(); body.put("mensaId", "SCHWEINFURT_THWS");
        given().contentType("application/json").body(body).post("/api/participants").then().statusCode(400).body("error.code", is("VALIDATION_FAILED"));
        body.put("mensaId", "missing");
        given().contentType("application/json").body(body).post("/api/participants").then().statusCode(400);
        body.put("mensaId", "WUERZBURG_ROENTGENRING"); body.put("slotIds", List.of("2026-11-02T11:00"));
        given().contentType("application/json").body(body).post("/api/participants").then().statusCode(400).body("error.code", is("UNKNOWN_SLOT"));
        assertTrue(participants.findAll().isEmpty());
    }
    @Test void enforcesConsentNicknameAndCatalogValidation() {
        var body = request(); body.put("consent", false);
        given().contentType("application/json").body(body).post("/api/participants").then().statusCode(400).body("error.code", is("CONSENT_REQUIRED"));
        body.put("consent", true); body.put("displayName", "a@b.de");
        given().contentType("application/json").body(body).post("/api/participants").then().statusCode(400).body("error.code", is("VALIDATION_FAILED"));
        body.put("displayName", "İpek"); body.put("languages", List.of("unknown"));
        given().contentType("application/json").body(body).post("/api/participants").then().statusCode(400);
    }
    @Test void deletingRegistrationRevokesToken() {
        String token = given().contentType("application/json").body(request()).post("/api/participants").then().statusCode(201).extract().path("accessToken");
        given().auth().oauth2(token).delete("/api/me").then().statusCode(204);
        given().auth().oauth2(token).get("/api/me").then().statusCode(401);
    }
}
