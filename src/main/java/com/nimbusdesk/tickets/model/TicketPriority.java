package com.nimbusdesk.tickets.model;

/**
 * Decisão de design (não detalhada no PROJECT_SPEC.md, que só menciona "prioridade" e
 * {@code Sla.prioridadeDefault"): 4 níveis (em vez dos 3 do com.nimbusflow.tickets.model.
 * TicketPriority) - URGENTE cobre o caso de indisponibilidade total pedido pelo domínio de
 * helpdesk de TI (ex.: servidor fora do ar), que não tem equivalente no domínio de "relato de
 * ocorrência" do NimbusFlow.
 */
public enum TicketPriority {
  BAIXA,
  MEDIA,
  ALTA,
  URGENTE
}
