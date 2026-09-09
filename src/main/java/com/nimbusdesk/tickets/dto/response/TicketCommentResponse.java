package com.nimbusdesk.tickets.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketCommentResponse(
    UUID id,
    UUID ticketId,
    String autorId,
    /** Resolvido em leitura via UserDirectoryService - não é uma coluna da entidade. */
    String autorNome,
    String mensagem,
    boolean interno,
    /** Anexos enviados junto com este comentário (ver TicketAttachment#comment) - embutido aqui
     *  pelo mesmo racional de TicketResponse#attachments (volume pequeno por comentário). */
    List<TicketAttachmentResponse> attachments,
    Instant createdAt) {
}
