package de.thws.lunch2.invite;

import de.thws.lunch2.domain.Participant;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InviteService {
    /** HACK-105 will validate the code and attach the friend pair. */
    public Participant attachIfPresent(Participant participant, String inviteCode) {
        return participant;
    }
}
