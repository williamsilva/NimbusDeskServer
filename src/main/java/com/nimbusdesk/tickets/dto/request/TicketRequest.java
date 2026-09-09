package com.nimbusdesk.tickets.dto.request;

import com.nimbusdesk.tickets.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Usado tanto na criação ({@code POST /bff/v1/tickets}) quanto na edição ({@code PUT
 * /bff/v1/tickets/{id}}). {@code prioridade} e {@code participantIds} são considerados só na
 * criação - o solicitante sempre informa {@code prioridade} explicitamente na abertura (2026-09-09,
 * não existe mais fallback pro extinto {@code sla.prioridadeDefault}, ver TicketService#create); a
 * edição (PUT) NUNCA altera prioridade nem participantes (fora do contrato do PROJECT_SPEC.md pra
 * este endpoint - participantes de um chamado já existente continuam só via {@code PUT
 * .../participants}, ver TicketService#replaceParticipants), os dois campos são simplesmente
 * ignorados por TicketService#update. {@code setorId}/{@code equipamentoRefId}/{@code
 * participantIds} são opcionais.
 */
public record TicketRequest(
    @NotBlank @Size(max = 200) String titulo,
    @NotBlank @Size(max = 2000) String descricao,
    @NotNull UUID categoriaId,
    UUID setorId,
    UUID equipamentoRefId,
    @NotNull TicketPriority prioridade,
    /** "Compartilhado com" já na abertura (2026-09-08) - o próprio solicitante (quem está abrindo)
     *  é filtrado silenciosamente se vier aqui, mesmo racional de TicketService#replaceParticipants
     *  (não faz sentido ser participante do próprio chamado, já é "meu chamado" por definição). */
    List<String> participantIds) {
}
