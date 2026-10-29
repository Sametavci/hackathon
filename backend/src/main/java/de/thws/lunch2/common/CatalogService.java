package de.thws.lunch2.common;

import de.thws.lunch2.domain.Campus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class CatalogService {
    private static final List<LanguageOption> LANGUAGES = List.of(
            new LanguageOption("en", "English"), new LanguageOption("de", "Deutsch"),
            new LanguageOption("tr", "Turkish"), new LanguageOption("ar", "Arabic"),
            new LanguageOption("es", "Spanish"), new LanguageOption("fr", "French"),
            new LanguageOption("it", "Italian"), new LanguageOption("pt", "Portuguese"),
            new LanguageOption("ru", "Russian"), new LanguageOption("uk", "Ukrainian"),
            new LanguageOption("zh", "Chinese"), new LanguageOption("hi", "Hindi"));
    private static final List<InterestOption> INTERESTS = List.of(
            new InterestOption("gaming", "Gaming", "🎮"), new InterestOption("travel", "Travel", "✈️"),
            new InterestOption("tech", "Technology", "💻"), new InterestOption("football", "Football", "⚽"),
            new InterestOption("music", "Music", "🎵"), new InterestOption("cooking", "Cooking", "🍳"),
            new InterestOption("hiking", "Hiking", "🥾"), new InterestOption("movies", "Movies", "🎬"),
            new InterestOption("photography", "Photography", "📷"), new InterestOption("art", "Art", "🎨"),
            new InterestOption("reading", "Reading", "📚"), new InterestOption("fitness", "Fitness", "💪"),
            new InterestOption("startups", "Startups", "🚀"), new InterestOption("languages", "Languages", "💬"),
            new InterestOption("anime", "Anime", "🌸"), new InterestOption("boardgames", "Board games", "🎲"));
    private static final List<StudyFieldOption> STUDY_FIELDS = List.of(
            new StudyFieldOption("COMPUTER_SCIENCE", "Computer Science"),
            new StudyFieldOption("ENGINEERING", "Engineering"), new StudyFieldOption("BUSINESS", "Business"),
            new StudyFieldOption("DESIGN", "Design"), new StudyFieldOption("SOCIAL_SCIENCES", "Social Sciences"),
            new StudyFieldOption("HEALTH", "Health"), new StudyFieldOption("OTHER", "Other"));

    private final List<CampusOption> campuses;

    @Inject
    public CatalogService(@ConfigProperty(name = "lunch2.location.wuerzburg") String wuerzburg,
                          @ConfigProperty(name = "lunch2.location.schweinfurt") String schweinfurt) {
        campuses = List.of(new CampusOption(Campus.WUERZBURG, "Würzburg", wuerzburg),
                new CampusOption(Campus.SCHWEINFURT, "Schweinfurt", schweinfurt));
    }

    public List<CampusOption> campuses() {
        return campuses;
    }

    public List<LanguageOption> languages() {
        return LANGUAGES;
    }

    public List<InterestOption> interests() {
        return INTERESTS;
    }

    public List<StudyFieldOption> studyFields() {
        return STUDY_FIELDS;
    }

    public boolean hasLanguage(String code) {
        return LANGUAGES.stream().anyMatch(language -> language.code().equals(code));
    }

    public boolean hasInterest(String id) {
        return INTERESTS.stream().anyMatch(interest -> interest.id().equals(id));
    }

    public boolean hasStudyField(String id) {
        return STUDY_FIELDS.stream().anyMatch(field -> field.id().equals(id));
    }

    public String languageLabel(String code) {
        return LANGUAGES.stream().filter(language -> language.code().equals(code))
                .findFirst().orElseThrow().label();
    }

    public String interestLabel(String id) {
        return INTERESTS.stream().filter(interest -> interest.id().equals(id)).findFirst().orElseThrow().label();
    }

    public String location(Campus campus) {
        return campuses.stream().filter(option -> option.id() == campus).findFirst().orElseThrow().location();
    }

    public record CampusOption(Campus id, String label, String location) {}
    public record LanguageOption(String code, String label) {}
    public record InterestOption(String id, String label, String emoji) {}
    public record StudyFieldOption(String id, String label) {}
}
