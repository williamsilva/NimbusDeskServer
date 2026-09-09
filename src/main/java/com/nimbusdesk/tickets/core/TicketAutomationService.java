package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.model.Ticket;
import com.nimbusdesk.tickets.model.TicketParticipant;
import com.nimbusdesk.tickets.model.TicketStatus;
import com.nimbusdesk.tickets.repository.TicketParticipantRepository;
import com.nimbusdesk.tickets.repository.TicketRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas de candidatos pros 3 jobs de automação de chamados (ver TicketAutomationSettings) -
 * separado de TicketService de propósito (não é CRUD de chamado, é leitura em lote pra um contexto
 * sem usuário logado/sem HTTP request, ver TicketUnassignedAlertJob/TicketPendingResponseAlertJob/
 * TicketAutoCloseJob). A mutação do auto-fechamento em si continua em TicketService#autoCloseResolved
 * (precisa reaproveitar #writeHistory/#applyStatusSideEffects, privados lá).
 */
@Service
@RequiredArgsConstructor
public class TicketAutomationService {

  private final TicketRepository ticketRepository;
  private final TicketParticipantRepository participantRepository;

  /** Base do e-mail "chamados sem responsável" (ver TicketNotificationService#notifyUnassignedAlert)
   *  - mesma query já usada pela "Fila de atendimento" (ver TicketRepository). */
  @Transactional(readOnly = true)
  public List<Ticket> findUnassignedOpenTickets() {
    return ticketRepository.findByStatusAndResponsavelIdIsNullOrderByDataAberturaAsc(TicketStatus.ABERTO);
  }

  /**
   * Base do e-mail "chamado aguardando sua resposta" (ver TicketNotificationService#
   * notifyPendingResponseAlert) - agrupa cada chamado ainda ativo com {@link Ticket#
   * getAguardandoAcaoDe()} preenchido pelo(s) destinatário(s) que precisam agir.
   * {@code aguardandoAcaoDe} guarda um único id (solicitante OU responsável, nunca participante -
   * ver TicketCommentService#applyWaitingAction), mas quando é o LADO do solicitante que está
   * pendente, os participantes ("compartilhado com") também entram na lista de destinatários -
   * mesmo racional de "lado do solicitante" já usado lá (participante é tratado como extensão do
   * solicitante pra fins de quem precisa responder).
   */
  @Transactional(readOnly = true)
  public Map<String, List<Ticket>> findTicketsPendingResponseByRecipient() {
    List<Ticket> tickets = ticketRepository.findByAguardandoAcaoDeIsNotNullAndStatusNotIn(
        TicketStatusTransitions.TERMINAL_STATUSES);

    Map<String, List<Ticket>> byRecipient = new LinkedHashMap<>();
    for (Ticket ticket : tickets) {
      for (String recipientId : recipientsFor(ticket)) {
        byRecipient.computeIfAbsent(recipientId, k -> new ArrayList<>()).add(ticket);
      }
    }
    return byRecipient;
  }

  private List<String> recipientsFor(Ticket ticket) {
    String waitingOn = ticket.getAguardandoAcaoDe();
    if (waitingOn.equals(ticket.getResponsavelId())) {
      return List.of(waitingOn);
    }

    List<String> recipients = new ArrayList<>();
    recipients.add(waitingOn);
    recipients.addAll(participantRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
        .map(TicketParticipant::getUserId)
        .toList());
    return recipients;
  }

  /** Base do job de auto-fechamento (ver TicketService#autoCloseResolved) - RESOLVIDO há pelo menos
   *  {@code carenciaDias} dias. Só ids (o job relê cada um por id na hora de fechar, evita carregar
   *  entidades inteiras em memória à toa se a lista for grande). */
  @Transactional(readOnly = true)
  public List<UUID> findTicketsEligibleForAutoClose(int carenciaDias) {
    Instant cutoff = Instant.now().minus(carenciaDias, ChronoUnit.DAYS);
    return ticketRepository.findIdsByStatusAndDataResolucaoBefore(TicketStatus.RESOLVIDO, cutoff);
  }
}
