package com.nimbusdesk.tickets.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SlaRequest(
    @NotBlank @Size(max = 120) String nome,
    @NotNull @Min(1) Integer tempoRespostaMin,
    @NotNull @Min(1) Integer tempoResolucaoMin) {
}
