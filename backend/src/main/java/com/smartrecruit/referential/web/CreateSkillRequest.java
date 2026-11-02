package com.smartrecruit.referential.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param name lettres, chiffres, espaces et quelques signes courants dans les noms techniques
 *             (C#, C++, .NET, CI/CD, Node.js)
 */
public record CreateSkillRequest(
        @NotBlank
        @Size(min = 2, max = 80)
        @Pattern(regexp = "[\\p{L}\\p{N} +#./&()'-]+", message = "contient des caractères non autorisés")
        String name) {
}
