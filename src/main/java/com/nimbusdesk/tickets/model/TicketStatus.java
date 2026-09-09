package com.nimbusdesk.tickets.model;

/**
 * Ver PROJECT_SPEC.md ("Modelo de domínio &gt; TicketStatus") pra tabela completa de transições
 * permitidas - validada em TicketService, sem state machine formal (mesmo padrão do
 * com.nimbusflow.tickets.core.TicketService).
 */
public enum TicketStatus {
  ABERTO,
  EM_ANDAMENTO,
  /** Técnico respondeu/agiu por último - agora é a vez do SOLICITANTE responder. */
  AGUARDANDO_SOLICITANTE,
  /** Solicitante respondeu por último - agora é a vez do T.I. responder (2026-09-08: muda
   *  automaticamente a cada comentário público trocado, ver TicketCommentService#applyWaitingAction -
   *  não precisa de ação manual do usuário, só reflete visualmente o mesmo "de quem é a vez"
   *  que já era rastreado internamente em Ticket#aguardandoAcaoDe). */
  AGUARDANDO_RESPOSTA,
  RESOLVIDO,
  FECHADO,
  CANCELADO
}
