package de.thws.lunch2.admin;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import de.thws.lunch2.domain.ConnectChoice;
import de.thws.lunch2.domain.MatchStatus;
import de.thws.lunch2.domain.PairStatus;
import de.thws.lunch2.persistence.ConnectionRepository;
import de.thws.lunch2.persistence.FriendPairRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class StatsService {
    private final ParticipantRepository participants;
    private final GroupRepository groups;
    private final FriendPairRepository pairs;
    private final ConnectionRepository connections;

    @Inject
    public StatsService(ParticipantRepository participants, GroupRepository groups,
            FriendPairRepository pairs, ConnectionRepository connections) {
        this.participants = participants;
        this.groups = groups;
        this.pairs = pairs;
        this.connections = connections;
    }

    public StatsResponse get() {
        var people = participants.findAll();
        var allGroups = groups.findAll();
        Map<Category, Long> registrations = new EnumMap<>(Category.class);
        Map<Campus, Long> byCampus = new EnumMap<>(Campus.class);
        for (Category category : Category.values()) registrations.put(category, 0L);
        for (Campus campus : Campus.values()) byCampus.put(campus, 0L);
        people.forEach(p -> {
            registrations.merge(p.category(), 1L, Long::sum);
            byCampus.merge(p.campus(), 1L, Long::sum);
        });
        long matched = people.stream().filter(p -> p.matchStatus() == MatchStatus.MATCHED).count();
        long waiting = people.stream().filter(p -> p.matchStatus() == MatchStatus.WAITING).count();
        Set<String> currentIds = people.stream().map(p -> p.id()).collect(Collectors.toSet());
        long mutual = 0;
        for (var group : allGroups) {
            Set<Direction> yes = connections.findByGroup(group.id()).stream()
                    .filter(c -> c.choice() == ConnectChoice.YES && !c.fromId().equals(c.toId()))
                    .filter(c -> currentIds.contains(c.fromId()) && currentIds.contains(c.toId()))
                    .filter(c -> group.memberIds().contains(c.fromId()) && group.memberIds().contains(c.toId()))
                    .map(c -> new Direction(c.fromId(), c.toId())).collect(Collectors.toSet());
            Set<Set<String>> reciprocal = new HashSet<>();
            for (Direction direction : yes) {
                if (yes.contains(new Direction(direction.to(), direction.from()))) {
                    reciprocal.add(Set.of(direction.from(), direction.to()));
                }
            }
            mutual += reciprocal.size();
        }
        double rate = people.isEmpty() ? 0 : Math.round((double) matched / people.size() * 100) / 100.0;
        return new StatsResponse(Map.copyOf(registrations), Map.copyOf(byCampus), matched, waiting, rate,
                allGroups.size(), pairs.findAll().stream().filter(p -> p.status() == PairStatus.ACTIVE).count(), mutual);
    }

    private record Direction(String from, String to) {}
}
