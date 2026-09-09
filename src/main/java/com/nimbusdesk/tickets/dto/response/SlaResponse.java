package com.nimbusdesk.tickets.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SlaResponse(
    UUID id,
    String nome,
    Integer tempoRespostaMin,
    Integer tempoResolucaoMin,
    Instant createdAt,
    Instant updatedAt) {
}
