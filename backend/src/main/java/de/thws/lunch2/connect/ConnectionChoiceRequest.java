package de.thws.lunch2.connect;

import de.thws.lunch2.domain.ConnectChoice;
import jakarta.validation.constraints.NotNull;

public record ConnectionChoiceRequest(@NotNull ConnectChoice choice) {}
