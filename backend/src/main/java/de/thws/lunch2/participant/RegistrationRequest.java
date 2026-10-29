package de.thws.lunch2.participant;

import de.thws.lunch2.domain.Campus;
import de.thws.lunch2.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegistrationRequest(
        @NotBlank @Size(min = 2, max = 30)
        @Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$", message = "Use letters, numbers, spaces, dots, underscores or hyphens.")
        String displayName,
        @NotNull Category category,
        @NotNull Campus campus,
        @NotNull @Size(min = 1, max = 4) List<@NotBlank String> languages,
        @NotNull @Size(min = 1, max = 5) List<@NotBlank String> interests,
        @NotNull @Size(min = 1, max = 15) List<@NotBlank String> slotIds,
        String studyField,
        String inviteCode,
        Boolean consent) {}
