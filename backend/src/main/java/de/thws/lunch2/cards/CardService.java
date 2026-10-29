package de.thws.lunch2.cards;

import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.domain.CardSet;
import de.thws.lunch2.domain.CardSource;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.persistence.GroupRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CardService {
    private final GroupRepository groups;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Inject
    public CardService(GroupRepository groups) {
        this.groups = groups;
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
            CardSet cards = new CardSet(language, CardSource.FALLBACK,
                    FallbackCards.pick(groupId, group.sharedInterests(), language));
            // Re-read before saving so a deleted group is not recreated or other fields overwritten.
            LunchGroup latest = requireMember(groupId, participantId);
            if (latest.cards() != null) return response(groupId, latest.cards());
            groups.save(new LunchGroup(latest.id(), latest.campus(), latest.slotId(), latest.location(), latest.memberIds(),
                    latest.sharedInterests(), latest.commonLanguages(), latest.whyThisGroup(), latest.score(), latest.createdAt(), cards));
            Log.info("Fallback conversation cards cached");
            return response(groupId, cards);
        }
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
