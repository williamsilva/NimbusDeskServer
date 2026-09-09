package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.model.Ticket;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job de alerta "chamados ABERTO sem responsável" pro T.I. (pedido do usuário em 2026-09-09) -
 * horário configurável via tela "Configurações" (ver TicketAutomationSettingsService/
 * TicketUnassignedAlertScheduler, que agenda/reagenda este {@link #run} em runtime - por isso não
 * há {@code @Scheduled} aqui, mesmo padrão de com.nimbusflow.works.core.WorkAutoCompleteJob).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketUnassignedAlertJob {

  private final TicketAutomationService automationService;
  private final TicketNotificationService ticketNotificationService;

  public void run() {
    List<Ticket> tickets = automationService.findUnassignedOpenTickets();
    if (tickets.isEmpty()) {
      log.info("Nenhum chamado ABERTO sem responsável - alerta pro T.I. não enviado");
      return;
    }

    log.info("Enviando alerta de {} chamado(s) sem responsável pro T.I.", tickets.size());
    ticketNotificationService.notifyUnassignedAlert(tickets);
  }
}
