package de.thws.lunch2.auth;

import de.thws.lunch2.common.ApiException;
import de.thws.lunch2.domain.Participant;
import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class CurrentParticipant {
    private Participant participant;

    public String id() {
        return get().id();
    }

    public Participant get() {
        if (participant == null) {
            throw new ApiException(401, "UNAUTHORIZED", "A valid access token is required.");
        }
        return participant;
    }

    void set(Participant participant) {
        this.participant = participant;
    }
}
