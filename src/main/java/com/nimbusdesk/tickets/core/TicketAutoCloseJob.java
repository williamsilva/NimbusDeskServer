package com.nimbusdesk.tickets.core;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job de auto-fechamento (RESOLVIDO -&gt; FECHADO) - pedido do usuário em 2026-09-09, com carência
 * explícita (confirmada via AskUserQuestion: "Sim, com carência separada") pra não fechar um
 * chamado resolvido há pouquíssimo tempo. Horário configurável via tela "Configurações" (ver
 * TicketAutomationSettingsService/TicketAutoCloseScheduler, que agenda/reagenda este {@link #run}
 * em runtime - por isso não há {@code @Scheduled} aqui, mesmo padrão de com.nimbusflow.works.core.
 * WorkAutoCompleteJob). Sem e-mail nesta automação (o usuário só pediu a transição de status em si).
 *
 * <p>O laço fica aqui, num bean separado de {@link TicketAutomationService}/{@link TicketService},
 * de propósito (mesmo racional de WorkAutoCompleteJob): cada chamada a {@code
 * ticketService.autoCloseResolved(id)} precisa passar pelo proxy Spring pra {@code @Transactional}
 * funcionar - uma chamada dentro da própria classe bypassaria o AOP. Cada chamado é processado e
 * commitado isoladamente (uma transação por item); uma falha num chamado específico é logada e não
 * interrompe os demais do lote.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketAutoCloseJob {

  private final TicketAutomationService automationService;
  private final TicketAutomationSettingsService settingsService;
  private final TicketService ticketService;

  public void run() {
    int carenciaDias = settingsService.getAutoCloseCarenciaDias();
    List<UUID> eligibleIds = automationService.findTicketsEligibleForAutoClose(carenciaDias);
    if (eligibleIds.isEmpty()) {
      log.info("Nenhum chamado Resolvido há mais de {} dia(s) - auto-fechamento não executado", carenciaDias);
      return;
    }

    log.info("Fechando automaticamente {} chamado(s) Resolvido há mais de {} dia(s)", eligibleIds.size(), carenciaDias);
    for (UUID id : eligibleIds) {
      try {
        ticketService.autoCloseResolved(id);
      } catch (Exception e) {
        log.error("Falha ao auto-fechar o chamado {}", id, e);
      }
    }
  }
}
