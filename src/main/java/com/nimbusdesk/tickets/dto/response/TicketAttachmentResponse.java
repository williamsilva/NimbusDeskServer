package com.nimbusdesk.tickets.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Sem URL aqui de propósito - a URL pré-assinada é obtida à parte, via {@code GET
 *  /bff/v1/tickets/{id}/attachments/{attachmentId}/url} (ver TicketAttachmentUrlResponse), TTL
 *  curto, nunca cacheada/persistida. */
public record TicketAttachmentResponse(
    UUID id,
    UUID ticketId,
    String nomeArquivo,
    String tipoMime,
    Long tamanhoBytes,
    Instant createdAt) {
}
