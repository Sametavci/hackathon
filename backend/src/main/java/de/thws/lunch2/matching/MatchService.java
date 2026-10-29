package de.thws.lunch2.matching;

import de.thws.lunch2.common.CatalogService;
import de.thws.lunch2.domain.FriendPair;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.domain.Participant;
import de.thws.lunch2.domain.ReasonCode;
import de.thws.lunch2.persistence.DataAdmin;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MatchService {
    private static final Logger LOG = Logger.getLogger(MatchService.class);
    private final ParticipantRepository participants;
    private final GroupRepository groups;
    private final FriendPairRepository pairs;
    private final DataAdmin dataAdmin;
    private final CatalogService catalog;

    @Inject
    public MatchService(ParticipantRepository participants, GroupRepository groups,
            FriendPairRepository pairs, DataAdmin dataAdmin, CatalogService catalog) {
        this.participants = participants;
        this.groups = groups;
        this.pairs = pairs;
        this.dataAdmin = dataAdmin;
        this.catalog = catalog;
    }

    // Serialize admin match/reset requests so two runs cannot assign the same waiting participants.
    public synchronized MatchRunResponse match() {
        List<Participant> waiting = participants.findAll().stream()
                .filter(p -> p.matchStatus() == MatchStatus.WAITING)
                .sorted(Comparator.comparing(Participant::id)).toList();
        Map<String, Participant> byId = waiting.stream().collect(Collectors.toMap(Participant::id, Function.identity()));
        Map<String, FriendPair> pairsById = pairs.findAll().stream()
                .collect(Collectors.toMap(FriendPair::id, Function.identity()));
        List<MatchCandidate> candidates = waiting.stream().map(p -> new MatchCandidate(p.id(), p.category(),
                p.campus(), Set.copyOf(p.languages()), Set.copyOf(p.interests()), Set.copyOf(p.slotIds()),
                p.studyField(), friendId(p, pairsById), p.createdAt())).toList();
        List<String> slots = candidates.stream().flatMap(c -> c.slotIds().stream()).distinct().sorted().toList();
        Set<Set<String>> previouslyMet = new HashSet<>();
        for (LunchGroup group : groups.findAll()) {
            for (int i = 0; i < group.memberIds().size(); i++) {
                for (int j = i + 1; j < group.memberIds().size(); j++) {
                    previouslyMet.add(Set.of(group.memberIds().get(i), group.memberIds().get(j)));
                }
            }
        }
        MatchOutput output = new MatchingEngine().match(new MatchInput(candidates, slots, previouslyMet));
        Map<String, MatchCandidate> candidateById = candidates.stream()
                .collect(Collectors.toMap(MatchCandidate::id, Function.identity()));
        // Validate the complete result before making the first persistent change.
        Set<String> assigned = new HashSet<>();
        for (ProposedGroup group : output.groups()) {
            if (!GroupValidator.violations(group, candidateById).isEmpty()) {
                throw new IllegalStateException("Matching proposed an invalid group");
            }
            for (String id : group.memberIds()) {
                if (!assigned.add(id) || output.waiting().containsKey(id)) {
                    throw new IllegalStateException("Matching assigned a participant more than once");
                }
            }
        }
        List<LunchGroup> planned = output.groups().stream().map(g -> new LunchGroup(UUID.randomUUID().toString(),
                g.campus(), g.slotId(), catalog.location(g.campus()), g.memberIds(), g.sharedInterests(),
                g.commonLanguages(), explain(g, byId, candidateById), g.score(), Instant.now(), null)).toList();
        List<MatchRunResponse.CreatedGroup> created = new ArrayList<>();
        for (LunchGroup group : planned) {
            groups.save(group);
            for (String id : group.memberIds()) participants.save(withMatch(byId.get(id), MatchStatus.MATCHED, group.id(), null));
            created.add(new MatchRunResponse.CreatedGroup(group.id(), group.campus(), group.slotId(),
                    group.memberIds().stream().map(id -> byId.get(id).displayName()).toList(), group.score()));
        }
        List<MatchRunResponse.WaitingParticipant> reasons = new ArrayList<>();
        output.waiting().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Participant participant = byId.get(entry.getKey());
            participants.save(withMatch(participant, MatchStatus.WAITING, null, entry.getValue()));
            reasons.add(new MatchRunResponse.WaitingParticipant(participant.id(), participant.displayName(), entry.getValue()));
        });
        LOG.infof("Match run created %d groups; %d participants waiting", created.size(), reasons.size());
        return new MatchRunResponse(List.copyOf(created), List.copyOf(reasons));
    }

    public synchronized void reset() {
        dataAdmin.wipeAll();
        LOG.info("Admin reset completed");
    }

    private static String friendId(Participant participant, Map<String, FriendPair> pairs) {
        FriendPair pair = pairs.get(participant.friendPairId());
        if (pair == null || pair.status() != PairStatus.ACTIVE) return null;
        if (participant.id().equals(pair.inviterId())) return pair.inviteeId();
        if (participant.id().equals(pair.inviteeId())) return pair.inviterId();
        throw new IllegalStateException("Participant references a friend pair they do not belong to");
    }

    private List<String> explain(ProposedGroup group, Map<String, Participant> byId, Map<String, MatchCandidate> candidates) {
        List<String> explanation = new ArrayList<>();
        for (String interest : group.sharedInterests()) {
            long count = group.memberIds().stream().filter(id -> byId.get(id).interests().contains(interest)).count();
            explanation.add(String.format(Locale.ROOT, "%d of you love %s", count, catalog.interestLabel(interest)));
        }
        for (String language : group.commonLanguages()) explanation.add("Everyone speaks " + catalog.languageLabel(language));
        for (String id : group.memberIds()) {
            String friend = candidates.get(id).friendId();
            if (friend != null && id.compareTo(friend) < 0) {
                explanation.add(byId.get(id).displayName() + " & " + byId.get(friend).displayName() + " signed up together");
            }
        }
        return List.copyOf(explanation);
    }

    private static Participant withMatch(Participant p, MatchStatus status, String groupId, ReasonCode reason) {
        return new Participant(p.id(), p.tokenHash(), p.displayName(), p.category(), p.campus(), p.languages(),
                p.interests(), p.slotIds(), p.studyField(), status, groupId, reason, p.friendPairId(), p.createdAt(), p.consentAt());
    }
}
