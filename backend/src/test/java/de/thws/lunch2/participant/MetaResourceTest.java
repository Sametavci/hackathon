package de.thws.lunch2.participant;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MetaResourceTest {
    @Test
    void catalogsExposeExactFrozenIdsAndConfiguredCampusLocationsWithoutAuthentication() {
        var response = given().when().get("/api/meta").then().statusCode(200).contentType(ContentType.JSON)
                .extract().jsonPath();
        assertEquals(Set.of("campuses", "languages", "interests", "studyFields", "slots"),
                response.getMap("").keySet());
        assertEquals(List.of("WUERZBURG", "SCHWEINFURT"), response.getList("campuses.id"));
        assertEquals("Würzburg", response.getString("campuses[0].label"));
        for (String location : response.<String>getList("campuses.location")) {
            assertFalse(location.isBlank());
        }
        assertEquals(List.of("en", "de", "tr", "ar", "es", "fr", "it", "pt", "ru", "uk", "zh", "hi"),
                response.getList("languages.code"));
        assertEquals(List.of("gaming", "travel", "tech", "football", "music", "cooking", "hiking", "movies",
                "photography", "art", "reading", "fitness", "startups", "languages", "anime", "boardgames"),
                response.getList("interests.id"));
        assertEquals(List.of("COMPUTER_SCIENCE", "ENGINEERING", "BUSINESS", "DESIGN", "SOCIAL_SCIENCES", "HEALTH", "OTHER"),
                response.getList("studyFields.id"));
    }

    @Test
    void slotsHaveLocalIdsChronologicalOrderAndContractShape() {
        List<Map<String, String>> slots = given().when().get("/api/meta").then().statusCode(200)
                .extract().jsonPath().getList("slots");
        assertEquals(15, slots.size());
        assertEquals(5, slots.stream().map(slot -> slot.get("date")).distinct().count());
        List<String> ids = slots.stream().map(slot -> slot.get("id")).toList();
        assertEquals(ids.stream().sorted().toList(), ids);
        for (var slot : slots) {
            assertEquals(Set.of("id", "date", "start", "end", "label"), slot.keySet());
            assertEquals(slot.get("date") + "T" + slot.get("start"), slot.get("id"));
            DayOfWeek day = LocalDate.parse(slot.get("date")).getDayOfWeek();
            assertTrue(day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY);
            assertEquals(45, ChronoUnit.MINUTES.between(LocalTime.parse(slot.get("start")), LocalTime.parse(slot.get("end"))));
        }
    }
}
