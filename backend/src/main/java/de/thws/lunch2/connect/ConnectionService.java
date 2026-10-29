package de.thws.lunch2.connect;

import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.domain.ConnectChoice;
import de.thws.lunch2.domain.ConnectionChoice;
import de.thws.lunch2.domain.ConnectionStatus;
import de.thws.lunch2.domain.LunchGroup;
import de.thws.lunch2.persistence.ConnectionRepository;
import de.thws.lunch2.persistence.GroupRepository;
import de.thws.lunch2.persistence.ParticipantRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ConnectionService {
    private final ConnectionRepository connections;
    private final GroupRepository groups;
    private final ParticipantRepository participants;

    @Inject
    public ConnectionService(ConnectionRepository connections, GroupRepository groups, ParticipantRepository participants) {
        this.connections = connections;
        this.groups = groups;
        this.participants = participants;
    }

    public synchronized List<ConnectionItem> list(String groupId, String callerId) {
        return requireGroup(groupId, callerId).memberIds().stream().filter(id -> !id.equals(callerId))
                .map(id -> item(groupId, callerId, id)).toList();
    }

    public synchronized ConnectionItem choose(String groupId, String callerId, String targetId, ConnectChoice choice) {
        requireTarget(groupId, callerId, targetId);
        String contact = choice == ConnectChoice.YES
                ? connections.find(groupId, callerId, targetId).map(ConnectionChoice::contact).orElse(null) : null;
        connections.save(new ConnectionChoice(groupId, callerId, targetId, choice, contact));
        Log.info("Connection choice updated");
        return item(groupId, callerId, targetId);
    }

    public synchronized ConnectionItem shareContact(String groupId, String callerId, String targetId, String contact) {
        requireTarget(groupId, callerId, targetId);
        if (!choseYes(groupId, callerId, targetId) || !choseYes(groupId, targetId, callerId)) {
            throw new ApiException(409, "NOT_MUTUAL", "You can share contact details after you both choose to connect.");
        }
        connections.save(new ConnectionChoice(groupId, callerId, targetId, ConnectChoice.YES, contact));
        Log.info("Connection contact shared");
        return item(groupId, callerId, targetId);
    }

    public synchronized ConnectionItem withdrawContact(String groupId, String callerId, String targetId) {
        requireTarget(groupId, callerId, targetId);
        connections.find(groupId, callerId, targetId).ifPresent(choice ->
                connections.save(new ConnectionChoice(groupId, callerId, targetId, choice.choice(), null)));
        Log.info("Connection contact withdrawn");
        return item(groupId, callerId, targetId);
    }

    private LunchGroup requireGroup(String groupId, String callerId) {
        LunchGroup group = groups.findById(groupId).orElseThrow(() ->
                new ApiException(404, "NOT_FOUND", "This lunch group was not found."));
        if (!group.memberIds().contains(callerId)) {
            throw new ApiException(403, "FORBIDDEN", "You are not a member of this lunch group.");
        }
        return group;
    }

    private void requireTarget(String groupId, String callerId, String targetId) {
        var caller = participants.findById(callerId).orElseThrow(() ->
                new ApiException(401, "UNAUTHORIZED", "A valid access token is required."));
        if (caller.groupId() == null) {
            throw new ApiException(409, "NOT_MATCHED", "You need a lunch group before choosing connections.");
        }
        LunchGroup group = requireGroup(groupId, callerId);
        if (targetId.equals(callerId)) {
            throw new ApiException(400, "VALIDATION_FAILED", "Please choose another member of your lunch group.");
        }
        if (!group.memberIds().contains(targetId)) {
            throw new ApiException(403, "FORBIDDEN", "This participant is not a member of your lunch group.");
        }
    }

    private boolean choseYes(String groupId, String fromId, String toId) {
        return connections.find(groupId, fromId, toId).map(choice -> choice.choice() == ConnectChoice.YES).orElse(false);
    }

    /** The only response projection: it never serializes another participant's choice. */
    private ConnectionItem item(String groupId, String callerId, String targetId) {
        ConnectionChoice mine = connections.find(groupId, callerId, targetId).orElse(null);
        ConnectionChoice theirs = connections.find(groupId, targetId, callerId).orElse(null);
        ConnectChoice myChoice = mine == null ? null : mine.choice();
        boolean mutual = myChoice == ConnectChoice.YES && theirs != null && theirs.choice() == ConnectChoice.YES;
        ConnectionStatus status = mutual ? ConnectionStatus.MUTUAL
                : myChoice == ConnectChoice.YES ? ConnectionStatus.WAITING_FOR_THEM : ConnectionStatus.NONE;
        String displayName = participants.findById(targetId).map(participant -> participant.displayName())
                .orElse("Former participant");
        return new ConnectionItem(targetId, displayName, myChoice, status,
                mine == null ? null : mine.contact(), mutual ? theirs.contact() : null);
    }
}
