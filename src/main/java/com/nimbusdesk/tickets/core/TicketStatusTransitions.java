package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.model.TicketStatus;
import java.util.Map;
import java.util.Set;

/**
 * Validação de transições de status permitidas (ver PROJECT_SPEC.md "TicketStatus (enum)") - sem
 * state machine formal, mesmo padrão do com.nimbusflow.tickets.core.TicketService. Classe pura
 * (sem dependência de Spring) de propósito, pra ser testável com JUnit puro (ver
 * TicketStatusTransitionsTest) sem precisar de contexto Spring nenhum.
 *
 * <p>{@code ABERTO -> EM_ANDAMENTO} DE PROPÓSITO não está no mapa abaixo: essa transição só
 * acontece via {@code PUT /bff/v1/tickets/{id}/assign} (que exige informar {@code responsavelId}),
 * nunca via {@code PUT /bff/v1/tickets/{id}/status} genérico - ver TicketService#assign vs
 * TicketService#changeStatus.
 *
 * <p>2026-09-08: {@code FECHADO} virou terminal DE VERDADE - {@code FECHADO -> EM_ANDAMENTO}
 * (reabertura) foi removido do mapa, decisão explícita do usuário ("fechado/cancelado não pode
 * mais mudar status"). {@code RESOLVIDO -> EM_ANDAMENTO} continua permitido (só ele pode reabrir,
 * ver TicketService#REOPEN_FROM, que também foi ajustado pra não incluir mais FECHADO).
 */
final class TicketStatusTransitions {

  /** RESOLVIDO/FECHADO/CANCELADO - chamado "encerrado": não aceita mais comentário (ver
   *  TicketCommentService#add) e (com exceção de RESOLVIDO -> EM_ANDAMENTO, ver {@link #ALLOWED})
   *  não muda mais de status. Único ponto de verdade - TicketService reaproveita esta mesma
   *  constante (não duplica), tanto pra esse cálculo quanto pros badges de contagem
   *  (TicketService#scopeCounts) e SLA estourado. */
  static final Set<TicketStatus> TERMINAL_STATUSES =
      Set.of(TicketStatus.RESOLVIDO, TicketStatus.FECHADO, TicketStatus.CANCELADO);

  private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
      TicketStatus.ABERTO, Set.of(TicketStatus.CANCELADO),
      TicketStatus.EM_ANDAMENTO, Set.of(
          TicketStatus.AGUARDANDO_SOLICITANTE, TicketStatus.AGUARDANDO_RESPOSTA,
          TicketStatus.RESOLVIDO, TicketStatus.CANCELADO),
      TicketStatus.AGUARDANDO_SOLICITANTE, Set.of(
          TicketStatus.EM_ANDAMENTO, TicketStatus.AGUARDANDO_RESPOSTA, TicketStatus.RESOLVIDO),
      TicketStatus.AGUARDANDO_RESPOSTA, Set.of(
          TicketStatus.EM_ANDAMENTO, TicketStatus.AGUARDANDO_SOLICITANTE, TicketStatus.RESOLVIDO),
      TicketStatus.RESOLVIDO, Set.of(TicketStatus.FECHADO, TicketStatus.EM_ANDAMENTO),
      TicketStatus.FECHADO, Set.of(),
      TicketStatus.CANCELADO, Set.of());

  private TicketStatusTransitions() {
  }

  static boolean isAllowed(TicketStatus from, TicketStatus to) {
    return ALLOWED.getOrDefault(from, Set.of()).contains(to);
  }

  static boolean isTerminal(TicketStatus status) {
    return TERMINAL_STATUSES.contains(status);
  }
}
