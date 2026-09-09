package com.nimbusdesk.tickets.dto.response;

/** Participante ("compartilhado com") - só userId+nome (resolvido via UserDirectoryService), sem
 *  id próprio nem timestamp - o frontend só precisa saber QUEM está na lista pra editar via
 *  replace-all (ver TicketParticipantsRequest), não precisa do id interno da linha. */
public record TicketParticipantResponse(String userId, String nome) {
}
