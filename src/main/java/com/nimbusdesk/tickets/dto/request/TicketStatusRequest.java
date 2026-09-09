package com.nimbusdesk.tickets.dto.request;

import com.nimbusdesk.tickets.model.TicketStatus;
import jakarta.validation.constraints.NotNull;

/**
 * DTO não listado no esqueleto de pacotes do PROJECT_SPEC.md (que lista só TicketAssignRequest
 * pra mudança de estado) - necessário pro corpo {@code {novoStatus}} do endpoint {@code PUT
 * /bff/v1/tickets/{id}/status} descrito na seção "Controllers/endpoints exatos". Adicionado por
 * necessidade, não é uma divergência de design.
 */
public record TicketStatusRequest(@NotNull TicketStatus novoStatus) {
}
