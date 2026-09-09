package com.nimbusdesk.tickets.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record SetorRequest(
    @NotBlank @Size(max = 120) String nome,
    @NotNull Set<String> userIds,
    boolean ativo) {
}
