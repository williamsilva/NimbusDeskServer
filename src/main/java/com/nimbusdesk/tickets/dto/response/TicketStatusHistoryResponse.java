package com.nimbusdesk.tickets.dto.response;

import com.nimbusdesk.tickets.model.TicketStatus;
import java.time.Instant;
import java.util.UUID;

public record TicketStatusHistoryResponse(
    UUID id,
    UUID ticketId,
    TicketStatus statusAnterior,
    TicketStatus statusNovo,
    String usuarioId,
    /** Resolvido em leitura via UserDirectoryService - não é uma coluna da entidade. */
    String usuarioNome,
    Instant changedAt) {
}
