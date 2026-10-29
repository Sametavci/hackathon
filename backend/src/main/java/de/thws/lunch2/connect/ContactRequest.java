package de.thws.lunch2.connect;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContactRequest(@NotNull @Size(min = 1, max = 100) String contact) {}
