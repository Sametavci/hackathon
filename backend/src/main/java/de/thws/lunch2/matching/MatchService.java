package de.thws.lunch2.matching;

import de.thws.lunch2.common.CatalogService;
import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.auth.TokenService;
import de.thws.lunch2.domain.Category;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import de.thws.lunch2.common.MensaService;
import java.util.LinkedHashMap;
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
    private final MensaService mensas;
    private final TokenService tokens;
    private final boolean demoMode;

    @Inject
    public MatchService(ParticipantRepository participants, GroupRepository groups,
            FriendPairRepository pairs, DataAdmin dataAdmin, CatalogService catalog, MensaService mensas,
            TokenService tokens, @ConfigProperty(name = "lunch2.demo-mode") boolean demoMode) {
        this.participants = participants;
        this.groups = groups;
        this.pairs = pairs;
        this.dataAdmin = dataAdmin;
        this.catalog = catalog;
        this.mensas = mensas;
        this.tokens = tokens;
        this.demoMode = demoMode;
    }

    /** Explicit demo-only completion; ordinary matching never creates fictional registrations. */
    public synchronized MatchRunResponse matchWithDemo() {
        if (!demoMode) throw new ApiException(403, "FORBIDDEN", "Demo matching is disabled.");
        MatchRunResponse result = match();
        List<MatchRunResponse.CreatedGroup> created = new ArrayList<>(result.groupsCreated());
        Set<String> skipped = new HashSet<>();
        int number = 1;
        while (true) {
            List<Participant> waiting = participants.findAll().stream().filter(p -> p.matchStatus() == MatchStatus.WAITING)
                    .sorted(Comparator.comparing(Participant::createdAt).thenComparing(Participant::id)).toList();
            Participant target = waiting.stream().filter(p -> !skipped.contains(p.id())).findFirst().orElse(null);
            if (target == null) break;
            Map<String, Participant> byId = waiting.stream().collect(Collectors.toMap(Participant::id, Function.identity()));
            Map<String, FriendPair> byPair = pairs.findAll().stream().collect(Collectors.toMap(FriendPair::id, Function.identity()));
            DemoParty party = demoParty(target, waiting, byId, byPair);
            if (party == null) { skipped.add(target.id()); continue; }
            Instant now = Instant.now();
            for (Category category : Category.values()) {
                long count = party.members().stream().filter(p -> p.category() == category).count();
                for (long i = count; i < 2; i++) {
                    participants.save(new Participant(UUID.randomUUID().toString(), tokens.hash(tokens.newToken()),
                            "Demo student " + number++ + " (demo)", category, target.campus(), List.of(party.language()),
                            target.interests(), List.of(party.slot()), target.studyField(), MatchStatus.WAITING, null,
                            ReasonCode.WAITING_FOR_NEXT_ROUND, null, now, now, target.mensaId()));
                }
            }
            result = match();
            created.addAll(result.groupsCreated());
            // Each successful iteration adds at most three people and consumes at least four.
            // The cap guards against inconsistent snapshots or a full capped matching pool.
            long remaining = participants.findAll().stream().filter(p -> p.matchStatus() == MatchStatus.WAITING).count();
            if (remaining >= waiting.size()) break;
        }
        LOG.infof("Demo matching created %d groups", created.size());
        return new MatchRunResponse(List.copyOf(created), result.waiting());
    }

    private DemoParty demoParty(Participant target, List<Participant> waiting,
            Map<String, Participant> byId, Map<String, FriendPair> byPair) {
        List<Participant> initial = demoUnit(target, byId, byPair);
        if (initial.isEmpty() || target.waitingReason() == ReasonCode.FRIEND_DIFFERENT_CAMPUS
                || target.waitingReason() == ReasonCode.FRIEND_DIFFERENT_MENSA) return null;
        DemoParty best = null;
        List<String> languages = target.languages().stream().sorted(Comparator.comparing((String l) -> !l.equals("en"))
                .thenComparing(Comparator.naturalOrder())).toList();
        for (String slot : target.slotIds().stream().sorted().toList()) {
            if (!mensas.allows(mensas.resolve(target.campus(), target.mensaId()), slot, target.mensaId() == null)) continue;
            for (String language : languages) {
                if (!compatible(initial, target, slot, language)) continue;
                List<Participant> chosen = new ArrayList<>(initial);
                for (Participant candidate : waiting) {
                    if (chosen.stream().anyMatch(p -> p.id().equals(candidate.id()))) continue;
                    List<Participant> unit = demoUnit(candidate, byId, byPair);
                    if (unit.isEmpty() || !compatible(unit, target, slot, language)) continue;
                    if (unit.stream().anyMatch(p -> chosen.stream().anyMatch(c -> c.id().equals(p.id())))) continue;
                    List<Participant> combined = new ArrayList<>(chosen);
                    combined.addAll(unit);
                    if (combined.stream().filter(p -> p.category() == Category.DOMESTIC).count() <= 2
                            && combined.stream().filter(p -> p.category() == Category.INTERNATIONAL).count() <= 2) {
                        chosen.addAll(unit);
                    }
                }
                if (best == null || chosen.size() > best.members().size()) best = new DemoParty(List.copyOf(chosen), slot, language);
            }
        }
        return best;
    }

    private static List<Participant> demoUnit(Participant participant, Map<String, Participant> byId,
            Map<String, FriendPair> byPair) {
        FriendPair pair = byPair.get(participant.friendPairId());
        if (pair != null && pair.status() != PairStatus.UNLINKED && pair.issue() != null
                && pair.issue() != ReasonCode.INVITER_ALREADY_MATCHED) return List.of();
        String friend = friendId(participant, byPair);
        if (friend == null) return List.of(participant);
        Participant other = byId.get(friend);
        return other == null ? List.of() : List.of(participant, other);
    }

    private boolean compatible(List<Participant> members, Participant target, String slot, String language) {
        return members.stream().allMatch(p -> p.campus() == target.campus() && mensaId(p).equals(mensaId(target))
                && p.slotIds().contains(slot) && p.languages().contains(language));
    }

    private record DemoParty(List<Participant> members, String slot, String language) {}

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
        // Partition by Mensa before running the pure matching engine.
        Map<String, ReasonCode> mensaIssues = new LinkedHashMap<>();
        for (FriendPair pair : pairsById.values()) {
            if (pair.status() == PairStatus.UNLINKED) continue;
            Participant a = byId.get(pair.inviterId()), b = byId.get(pair.inviteeId());
            if (a == null || b == null) continue;
            ReasonCode issue = a.campus() != b.campus() ? ReasonCode.FRIEND_DIFFERENT_CAMPUS
                    : !mensaId(a).equals(mensaId(b)) ? ReasonCode.FRIEND_DIFFERENT_MENSA : null;
            if (issue != null) { mensaIssues.put(a.id(), issue); mensaIssues.put(b.id(), issue); }
        }
        List<ProposedGroup> proposals = new ArrayList<>();
        for (MensaService.Mensa mensa : mensas.mensas()) {
            List<MatchCandidate> pool = candidates.stream().filter(c -> mensaId(byId.get(c.id())).equals(mensa.id()))
                    .filter(c -> !mensaIssues.containsKey(c.id())).toList();
            MatchOutput partition = new MatchingEngine().match(new MatchInput(pool, slots, previouslyMet));
            proposals.addAll(partition.groups());
            mensaIssues.putAll(partition.waiting());
        }
        MatchOutput output = new MatchOutput(List.copyOf(proposals), Map.copyOf(mensaIssues));
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
                g.campus(), g.slotId(), groupLocation(g, byId), g.memberIds(), g.sharedInterests(),
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

    private String mensaId(Participant participant) {
        return mensas.resolve(participant.campus(), participant.mensaId()).id();
    }

    private String groupLocation(ProposedGroup group, Map<String, Participant> byId) {
        // Preserve configured locations for old clients/snapshots with no Mensa preference.
        if (group.memberIds().stream().allMatch(id -> byId.get(id).mensaId() == null)) return catalog.location(group.campus());
        Participant participant = byId.get(group.memberIds().getFirst());
        return mensas.location(mensas.resolve(participant.campus(), participant.mensaId()));
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
                p.interests(), p.slotIds(), p.studyField(), status, groupId, reason, p.friendPairId(), p.createdAt(), p.consentAt(), p.mensaId());
    }
}
