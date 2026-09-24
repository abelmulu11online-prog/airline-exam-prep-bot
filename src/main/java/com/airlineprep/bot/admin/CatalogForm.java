package com.airlineprep.bot.admin;

import jakarta.validation.constraints.*;

public record CatalogForm(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,39}") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Size(max = 100) String nameAm,
        Boolean active,
        @NotNull @Min(0) Integer displayOrder,
        @Positive Long examTypeId) {
    public CatalogForm { active = Boolean.TRUE.equals(active); }
}
