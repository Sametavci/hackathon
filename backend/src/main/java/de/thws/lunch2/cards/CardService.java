package de.thws.lunch2.cards;

import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.domain.CardSet;
import de.thws.lunch2.domain.CardSource;
import de.thws.lunch2.domain.Card;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.persistence.GroupRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CardService {
    private final GroupRepository groups;
    private final ConversationCardGenerator generator;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Inject
    public CardService(GroupRepository groups, GeminiCardGenerator generator) {
        this(groups, (ConversationCardGenerator) generator);
    }

    public CardService(GroupRepository groups) {
        this(groups, (interests, language) -> List.of());
    }

    CardService(GroupRepository groups, ConversationCardGenerator generator) {
        this.groups = groups;
        this.generator = generator;
    }

    public CardsResponse get(String groupId, String participantId) {
        requireMember(groupId, participantId);
        synchronized (locks.computeIfAbsent(groupId, ignored -> new Object())) {
            LunchGroup group = requireMember(groupId, participantId);
            if (group.cards() != null) {
                Log.info("Conversation cards served from cache");
                return response(groupId, group.cards());
            }
            String language = group.commonLanguages().contains("en") ? "en" : group.commonLanguages().getFirst();
            CardSet cards = generate(group, language);
            // Re-read before saving so a deleted group is not recreated or other fields overwritten.
            LunchGroup latest = requireMember(groupId, participantId);
            if (latest.cards() != null) return response(groupId, latest.cards());
            groups.save(new LunchGroup(latest.id(), latest.campus(), latest.slotId(), latest.location(), latest.memberIds(),
                    latest.sharedInterests(), latest.commonLanguages(), latest.whyThisGroup(), latest.score(), latest.createdAt(), cards));
            Log.infof("Conversation cards cached with source %s", cards.source());
            return response(groupId, cards);
        }
    }

    private CardSet generate(LunchGroup group, String language) {
        List<Card> fallback = FallbackCards.pick(group.id(), group.sharedInterests(), language);
        try {
            List<String> questions = generator.generate(group.sharedInterests(), language);
            if (questions != null && questions.size() >= 3 && questions.size() <= 6
                    && questions.stream().allMatch(q -> q != null && !q.isBlank() && q.length() <= 140)
                    && questions.stream().map(String::trim).distinct().count() == questions.size()) {
                List<String> selected = new ArrayList<>(questions.stream().map(String::trim).limit(5).toList());
                for (Card card : fallback) {
                    if (selected.size() == 5) break;
                    if (!selected.contains(card.text())) selected.add(card.text());
                }
                List<Card> cards = new ArrayList<>();
                for (int i = 0; i < selected.size(); i++) cards.add(new Card("c" + (i + 1), selected.get(i), "generic"));
                return new CardSet(language, CardSource.AI, cards);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Log.warn("Card generation interrupted; using fallback cards");
        } catch (Exception exception) {
            // Do not log provider response bodies, prompts or credentials.
            Log.warn("Card generation unavailable; using fallback cards");
        }
        return new CardSet(language, CardSource.FALLBACK, fallback);
    }

    private LunchGroup requireMember(String groupId, String participantId) {
        LunchGroup group = groups.findById(groupId)
                .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "Group not found."));
        if (!group.memberIds().contains(participantId)) {
            throw new ApiException(403, "FORBIDDEN", "Only members can view this group's cards.");
        }
        return group;
    }

    private static CardsResponse response(String groupId, CardSet cards) {
        return new CardsResponse(groupId, cards.language(), cards.source(), cards.cards());
    }
}
