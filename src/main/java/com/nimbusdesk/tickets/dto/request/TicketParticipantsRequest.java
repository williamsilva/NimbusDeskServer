package com.nimbusdesk.tickets.dto.request;

import java.util.List;

/** Replace-all da lista de participantes (ver TicketService#replaceParticipants) - lista vazia é
 *  válida (remove todos). Sem @NotEmpty de propósito - "sem participante nenhum" é um estado
 *  legítimo (o padrão, na verdade). */
public record TicketParticipantsRequest(List<String> userIds) {
}
