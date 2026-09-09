package com.nimbusdesk.tickets.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CategoryRequest(
    @NotBlank @Size(max = 120) String nome,
    @NotNull UUID slaId,
    boolean ativo) {
}
