package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.model.Ticket;

/**
 * "É meu chamado?" (solicitante, responsável atribuído, OU participante - ver
 * TicketParticipant) - usado por TicketService/TicketCommentService/TicketAttachmentService pra
 * decidir visibilidade (ver PROJECT_SPEC.md: "visível se for meu... OU se tiver CHAMADO_CONSULT").
 * Classe utilitária pura (sem Spring) de propósito - cada Service já injeta CurrentUserProvider/
 * NdSecurity/TicketParticipantRepository pros seus próprios gates, então não há necessidade de um
 * @Component extra só pra isto. {@code isParticipant} é resolvido fora (query ao
 * TicketParticipantRepository) porque esta classe não tem acesso a repository nenhum de propósito.
 */
final class TicketVisibility {

  private TicketVisibility() {
  }

  static boolean isMine(Ticket ticket, String currentUserId, boolean isParticipant) {
    if (currentUserId == null) {
      return false;
    }
    return currentUserId.equals(ticket.getSolicitanteId())
        || currentUserId.equals(ticket.getResponsavelId())
        || isParticipant;
  }
}
