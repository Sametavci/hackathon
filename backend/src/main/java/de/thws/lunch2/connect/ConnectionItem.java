package de.thws.lunch2.connect;

import de.thws.lunch2.domain.ConnectChoice;
import de.thws.lunch2.domain.ConnectionStatus;

public record ConnectionItem(String participantId, String displayName, ConnectChoice myChoice,
                             ConnectionStatus status, String myContact, String theirContact) {}
