package com.nimbusdesk.tickets.model;

/**
 * Canal de origem do chamado - só PORTAL é de fato implementado nesta fase (todo chamado criado
 * via {@code POST /bff/v1/tickets} recebe PORTAL). EMAIL/TELEFONE são placeholders documentados no
 * PROJECT_SPEC.md pra uma futura ingestão automática (fora de escopo agora).
 */
public enum TicketChannel {
  PORTAL,
  EMAIL,
  TELEFONE
}
