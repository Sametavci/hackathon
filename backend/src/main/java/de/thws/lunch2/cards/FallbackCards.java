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

    private static final Map<String, List<String>> GENERIC = Map.ofEntries(
            Map.entry("en", List.of("What small thing makes a campus day more enjoyable?", "What would you name this lunch group?",
                    "Which fictional character would be a fun lunch guest?", "What skill would you like to try learning?",
                    "What would an ideal study break look like?", "What kind of campus event would you enjoy?",
                    "What fun fact have you learned recently?", "What could this group explore together next?")),
            Map.entry("de", List.of("Was macht einen Tag am Campus für dich schöner?", "Wie würdest du diese Mittagsrunde nennen?",
                    "Welche erfundene Figur wäre ein lustiger Gast beim Mittagessen?", "Welche neue Fähigkeit würdest du gern ausprobieren?",
                    "Wie sieht eine ideale Lernpause für dich aus?", "Welche Veranstaltung am Campus würde dir gefallen?",
                    "Welche spannende Kleinigkeit hast du kürzlich gelernt?", "Was könnte diese Gruppe als Nächstes gemeinsam erkunden?")),
            // Other catalog languages get generic cards in the actual shared language, never mislabeled English.
            Map.entry("tr", List.of("Kampüste bir günü senin için daha keyifli yapan nedir?", "Bu öğle yemeği grubuna ne ad verirdin?",
                    "Hangi kurgusal karakterle öğle yemeği yemek eğlenceli olurdu?", "Hangi yeni beceriyi öğrenmek isterdin?", "İdeal bir ders molası nasıl olurdu?")),
            Map.entry("ar", List.of("ما الذي يجعل يومك في الجامعة أكثر متعة؟", "ما الاسم الذي تختاره لهذه المجموعة؟",
                    "أي شخصية خيالية ستكون ضيفاً ممتعاً على الغداء؟", "ما المهارة الجديدة التي تود تعلمها؟", "كيف تتخيل استراحة دراسية مثالية؟")),
            Map.entry("es", List.of("¿Qué hace más agradable un día en el campus?", "¿Qué nombre le pondrías a este grupo?",
                    "¿Qué personaje de ficción sería un invitado divertido?", "¿Qué habilidad nueva te gustaría aprender?", "¿Cómo sería tu pausa de estudio ideal?")),
            Map.entry("fr", List.of("Qu’est-ce qui rend une journée sur le campus plus agréable ?", "Quel nom donnerais-tu à ce groupe ?",
                    "Quel personnage fictif serait un invité amusant ?", "Quelle nouvelle compétence aimerais-tu apprendre ?", "À quoi ressemble ta pause idéale ?")),
            Map.entry("it", List.of("Che cosa rende più piacevole una giornata al campus?", "Che nome daresti a questo gruppo?",
                    "Quale personaggio immaginario sarebbe un ospite divertente?", "Quale nuova abilità vorresti imparare?", "Come sarebbe la tua pausa di studio ideale?")),
            Map.entry("pt", List.of("O que torna um dia no campus mais agradável?", "Que nome darias a este grupo?",
                    "Que personagem de ficção seria uma companhia divertida?", "Que nova habilidade gostarias de aprender?", "Como seria a tua pausa de estudo ideal?")),
            Map.entry("ru", List.of("Что делает день в университете приятнее?", "Как бы ты назвал эту группу?",
                    "Какого вымышленного героя ты бы пригласил на обед?", "Какой новый навык ты хотел бы освоить?", "Каким был бы твой идеальный перерыв в учёбе?")),
            Map.entry("uk", List.of("Що робить день в університеті приємнішим?", "Як би ти назвав цю групу?",
                    "Якого вигаданого героя ти запросив би на обід?", "Яку нову навичку ти хотів би опанувати?", "Якою була б твоя ідеальна перерва в навчанні?")),
            Map.entry("zh", List.of("什么小事会让校园生活更有趣？", "你会给这个午餐小组取什么名字？",
                    "你想邀请哪个虚构角色一起吃午饭？", "你想尝试学习什么新技能？", "你理想中的课间休息是什么样的？")),
            Map.entry("hi", List.of("कैंपस में आपका दिन और मज़ेदार क्या बनाता है?", "आप इस समूह का क्या नाम रखेंगे?",
                    "किस काल्पनिक किरदार के साथ खाना मज़ेदार होगा?", "आप कौन सा नया कौशल सीखना चाहेंगे?", "पढ़ाई के बीच आपका आदर्श विराम कैसा होगा?")));

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
        List<String> generic = new ArrayList<>(GENERIC.getOrDefault(lang, GENERIC.get("en")));
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
