package com.nimbusdesk.tickets.dto.response;

/**
 * Contagens pros badges dos 5 escopos da lista de chamados (ver TicketService#scopeCounts/
 * TicketController#scopeCounts) - todas já excluem chamados "encerrados" (RESOLVIDO/FECHADO/
 * CANCELADO, ver TicketService#TERMINAL_STATUSES), só contam trabalho ainda em aberto.
 *
 * <p>{@code all} TEM contagem própria (2026-09-08, não é mais soma de mine+waiting+shared+queue no
 * frontend) - um chamado pode satisfazer mais de um escopo ao mesmo tempo (ex. sou solicitante E
 * estou aguardando ação nele), então somar os 4 contava ele em dobro. {@code all} conta
 * diretamente a base real do escopo "all" em #search (nenhum filtro de dono/participante, só
 * exclui "encerrado").
 */
public record TicketScopeCountsResponse(long mine, long waiting, long shared, long queue, long all) {
}
