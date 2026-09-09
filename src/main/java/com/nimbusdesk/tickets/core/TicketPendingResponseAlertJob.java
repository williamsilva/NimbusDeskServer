package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.model.Ticket;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job de alerta "chamado aguardando SUA resposta" pro solicitante/compartilhados ou responsável
 * (pedido do usuário em 2026-09-09) - um e-mail por destinatário, cada um só com os chamados que
 * ELE precisa responder (ver TicketAutomationService#findTicketsPendingResponseByRecipient).
 * Horário configurável via tela "Configurações" (ver TicketAutomationSettingsService/
 * TicketPendingResponseAlertScheduler, que agenda/reagenda este {@link #run} em runtime - por isso
 * não há {@code @Scheduled} aqui).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketPendingResponseAlertJob {

  private final TicketAutomationService automationService;
  private final TicketNotificationService ticketNotificationService;

  public void run() {
    Map<String, List<Ticket>> byRecipient = automationService.findTicketsPendingResponseByRecipient();
    if (byRecipient.isEmpty()) {
      log.info("Nenhum chamado aguardando resposta de alguém - alerta de pendência não enviado");
      return;
    }

    log.info("Enviando alerta de pendência de resposta pra {} destinatário(s)", byRecipient.size());
    byRecipient.forEach((userId, tickets) -> {
      try {
        ticketNotificationService.notifyPendingResponseAlert(userId, tickets);
      } catch (Exception e) {
        log.error("Falha ao notificar pendência de resposta pro usuário {}", userId, e);
      }
    });
  }
}
