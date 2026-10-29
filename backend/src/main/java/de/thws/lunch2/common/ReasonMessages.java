package de.thws.lunch2.common;

import de.thws.lunch2.domain.ReasonCode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class ReasonMessages {
    private static final Map<ReasonCode, String> MESSAGES = Map.of(
            ReasonCode.WAITING_FOR_NEXT_ROUND, "Matching has not run since you registered.",
            ReasonCode.NOT_ENOUGH_DOMESTIC, "Not enough locally-based students share your campus & time slots yet.",
            ReasonCode.NOT_ENOUGH_INTERNATIONAL, "Not enough international students share your campus & time slots yet.",
            ReasonCode.NO_COMMON_LANGUAGE, "No group with a shared language is possible yet. Adding a language helps.",
            ReasonCode.FRIEND_DIFFERENT_CAMPUS, "You and your friend chose different campuses.",
            ReasonCode.FRIEND_NO_COMMON_SLOT, "You and your friend have no lunch slot in common.",
            ReasonCode.FRIEND_NO_COMMON_LANGUAGE, "You and your friend have no language in common.",
            ReasonCode.INVITER_ALREADY_MATCHED, "Your friend was already matched before you joined; you'll be matched on your own.");

    public String message(ReasonCode code) {
        return MESSAGES.get(code);
    }
}
