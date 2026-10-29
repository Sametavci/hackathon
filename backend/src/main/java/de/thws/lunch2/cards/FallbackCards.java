package de.thws.lunch2.cards;

import de.thws.lunch2.domain.Card;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class FallbackCards {
    private FallbackCards() {}

    private record Questions(List<String> en, List<String> de) {}
    private record Question(String text, String topic) {}

    private static Questions questions(String en1, String en2, String de1, String de2) {
        return new Questions(List.of(en1, en2), List.of(de1, de2));
    }

    private static final Map<String, Questions> INTERESTS = Map.ofEntries(
            Map.entry("gaming", questions("Which game would you recommend to this table?", "What makes a game fun to play together?",
                    "Welches Spiel würdest du dieser Runde empfehlen?", "Was macht ein Spiel in einer Gruppe besonders unterhaltsam?")),
            Map.entry("travel", questions("Which place would you like to explore on a day trip?", "What do you enjoy discovering in a new city?",
                    "Welchen Ort würdest du gern bei einem Tagesausflug erkunden?", "Was entdeckst du gern in einer neuen Stadt?")),
            Map.entry("tech", questions("What invention would make campus life easier?", "Which technology would you like to understand better?",
                    "Welche Erfindung würde den Campusalltag erleichtern?", "Welche Technologie würdest du gern besser verstehen?")),
            Map.entry("football", questions("What makes a football match exciting to watch?", "What would you name a team formed at this table?",
                    "Was macht ein Fußballspiel spannend?", "Wie würdest du eine Mannschaft aus dieser Runde nennen?")),
            Map.entry("music", questions("What song would you add to a shared lunch playlist?", "Which instrument would you like to try?",
                    "Welches Lied gehört auf eine gemeinsame Mittagspausen-Playlist?", "Welches Instrument würdest du gern ausprobieren?")),
            Map.entry("cooking", questions("Which dish would you enjoy cooking with a group?", "What ingredient would inspire a fun cooking challenge?",
                    "Welches Gericht würdest du gern mit einer Gruppe kochen?", "Welche Zutat wäre spannend für eine Koch-Challenge?")),
            Map.entry("hiking", questions("What makes a walking route interesting to you?", "Would you rather explore a forest path or a riverside trail?",
                    "Was macht einen Wanderweg für dich interessant?", "Würdest du lieber einen Waldweg oder einen Flussweg erkunden?")),
            Map.entry("movies", questions("What film would you suggest for a group movie night?", "Which fictional film location would you like to visit?",
                    "Welchen Film würdest du für einen gemeinsamen Filmabend vorschlagen?", "Welchen erfundenen Filmort würdest du gern besuchen?")),
            Map.entry("photography", questions("What small campus detail would make a good photo?", "Would you rather photograph a landscape or a city scene?",
                    "Welches kleine Detail am Campus wäre ein gutes Fotomotiv?", "Fotografierst du lieber Landschaften oder Stadtszenen?")),
            Map.entry("art", questions("What kind of art would you like to try making?", "What would a mural designed by this table look like?",
                    "Welche Kunstform würdest du gern ausprobieren?", "Wie würde ein Wandbild aussehen, das diese Runde gestaltet?")),
            Map.entry("reading", questions("What book would make a good group discussion?", "Which fictional world would you like to explore?",
                    "Welches Buch wäre interessant für eine gemeinsame Diskussion?", "Welche erfundene Welt würdest du gern erkunden?")),
            Map.entry("fitness", questions("Which activity would be fun to try as a group?", "What music would you pick for a team activity?",
                    "Welche Aktivität würdest du gern mit einer Gruppe ausprobieren?", "Welche Musik passt für dich zu einer Teamaktivität?")),
            Map.entry("startups", questions("What everyday campus problem could a new idea solve?", "What would you call a project created by this table?",
                    "Welches Alltagsproblem am Campus könnte eine neue Idee lösen?", "Wie würde ein gemeinsames Projekt dieser Runde heißen?")),
            Map.entry("languages", questions("What word do you enjoy saying in another language?", "What playful way of learning a language would you try?",
                    "Welches Wort sprichst du gern in einer anderen Sprache aus?", "Welche spielerische Methode zum Sprachenlernen würdest du testen?")),
            Map.entry("anime", questions("Which anime would you recommend to a curious newcomer?", "What makes an animated story memorable to you?",
                    "Welchen Anime würdest du neugierigen Einsteigern empfehlen?", "Was macht eine animierte Geschichte für dich unvergesslich?")),
            Map.entry("boardgames", questions("Which board game works well with four players?", "Would you choose a cooperative game or a friendly competition?",
                    "Welches Brettspiel eignet sich gut für vier Personen?", "Würdest du ein kooperatives Spiel oder einen freundlichen Wettbewerb wählen?")));

    public static List<Card> pick(String groupId, List<String> interestIds, String lang) {
        List<Question> interests = new ArrayList<>();
        interestIds.stream().distinct().sorted().forEach(id -> {
            Questions bank = INTERESTS.get(id);
            if (bank != null && (lang.equals("en") || lang.equals("de"))) {
                (lang.equals("de") ? bank.de() : bank.en()).forEach(text -> interests.add(new Question(text, id)));
            }
        });
        Collections.shuffle(interests, new Random(groupId.hashCode()));
        List<Question> selected = new ArrayList<>(interests.subList(0, Math.min(3, interests.size())));
        List<String> generic = new ArrayList<>(IcebreakerQuestions.forLanguage(lang));
        Collections.shuffle(generic, new Random(groupId.hashCode() ^ 0x5DEECE66DL));
        for (String text : generic) {
            if (selected.size() == 5) break;
            selected.add(new Question(text, "generic"));
        }
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            Question question = selected.get(i);
            cards.add(new Card("c" + (i + 1), question.text(), question.topic()));
        }
        return List.copyOf(cards);
    }
}
