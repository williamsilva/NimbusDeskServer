package com.nimbusdesk.tickets.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusdesk.tickets.model.TicketStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * JUnit puro (sem contexto Spring) pra validação de transição de status - ver PROJECT_SPEC.md
 * "TicketStatus (enum)" pra tabela completa. Bônus opcional sugerido pela tarefa (lógica de
 * transição complexa o suficiente pra justificar um teste, mesmo sem exigência de suíte completa
 * nesta fase).
 */
class TicketStatusTransitionsTest {

  @ParameterizedTest
  @CsvSource({
      "ABERTO, CANCELADO",
      "EM_ANDAMENTO, AGUARDANDO_SOLICITANTE",
      "EM_ANDAMENTO, AGUARDANDO_RESPOSTA",
      "EM_ANDAMENTO, RESOLVIDO",
      "EM_ANDAMENTO, CANCELADO",
      "AGUARDANDO_SOLICITANTE, EM_ANDAMENTO",
      "AGUARDANDO_SOLICITANTE, AGUARDANDO_RESPOSTA",
      "AGUARDANDO_SOLICITANTE, RESOLVIDO",
      "AGUARDANDO_RESPOSTA, EM_ANDAMENTO",
      "AGUARDANDO_RESPOSTA, AGUARDANDO_SOLICITANTE",
      "AGUARDANDO_RESPOSTA, RESOLVIDO",
      "RESOLVIDO, FECHADO",
      "RESOLVIDO, EM_ANDAMENTO",
  })
  void allowsEveryTransitionDocumentedInProjectSpec(TicketStatus from, TicketStatus to) {
    assertTrue(TicketStatusTransitions.isAllowed(from, to), from + " -> " + to + " should be allowed");
  }

  /**
   * ABERTO -> EM_ANDAMENTO É PROPOSITALMENTE recusado pelo endpoint genérico de status - essa
   * transição só acontece via {@code PUT /bff/v1/tickets/{id}/assign} (que exige responsavelId),
   * ver TicketService#assign.
   */
  @Test
  void rejectsAbertoToEmAndamento_becauseItIsAssignOnly() {
    assertFalse(TicketStatusTransitions.isAllowed(TicketStatus.ABERTO, TicketStatus.EM_ANDAMENTO));
  }

  @ParameterizedTest
  @CsvSource({
      "ABERTO, RESOLVIDO",
      "ABERTO, FECHADO",
      "AGUARDANDO_SOLICITANTE, CANCELADO",
      "AGUARDANDO_SOLICITANTE, FECHADO",
      "AGUARDANDO_RESPOSTA, CANCELADO",
      "AGUARDANDO_RESPOSTA, FECHADO",
      "AGUARDANDO_RESPOSTA, ABERTO",
      "FECHADO, CANCELADO",
      "FECHADO, RESOLVIDO",
      "FECHADO, EM_ANDAMENTO",
      "CANCELADO, EM_ANDAMENTO",
      "CANCELADO, ABERTO",
      "RESOLVIDO, CANCELADO",
  })
  void rejectsTransitionsNotDocumentedInProjectSpec(TicketStatus from, TicketStatus to) {
    assertFalse(TicketStatusTransitions.isAllowed(from, to), from + " -> " + to + " should NOT be allowed");
  }

  @Test
  void cancelledIsAlwaysTerminal() {
    for (TicketStatus target : TicketStatus.values()) {
      assertFalse(TicketStatusTransitions.isAllowed(TicketStatus.CANCELADO, target),
          "CANCELADO -> " + target + " should never be allowed");
    }
  }

  /** 2026-09-08: FECHADO virou terminal de verdade - decisão do usuário (fechado/cancelado não
   *  mudam mais de status nenhum, diferente de RESOLVIDO, que ainda pode voltar pra
   *  EM_ANDAMENTO). */
  @Test
  void closedIsAlwaysTerminal() {
    for (TicketStatus target : TicketStatus.values()) {
      assertFalse(TicketStatusTransitions.isAllowed(TicketStatus.FECHADO, target),
          "FECHADO -> " + target + " should never be allowed");
    }
  }

  @ParameterizedTest
  @CsvSource({"RESOLVIDO", "FECHADO", "CANCELADO"})
  void terminalStatusesAreTerminal(TicketStatus status) {
    assertTrue(TicketStatusTransitions.isTerminal(status), status + " should be terminal");
  }

  @ParameterizedTest
  @CsvSource({"ABERTO", "EM_ANDAMENTO", "AGUARDANDO_SOLICITANTE", "AGUARDANDO_RESPOSTA"})
  void nonTerminalStatusesAreNotTerminal(TicketStatus status) {
    assertFalse(TicketStatusTransitions.isTerminal(status), status + " should NOT be terminal");
  }
}
