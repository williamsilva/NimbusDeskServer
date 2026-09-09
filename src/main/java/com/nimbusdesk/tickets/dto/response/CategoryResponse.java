package com.nimbusdesk.tickets.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
    UUID id,
    String nome,
    UUID slaId,
    String slaNome,
    boolean ativo,
    Instant createdAt,
    Instant updatedAt) {
}
