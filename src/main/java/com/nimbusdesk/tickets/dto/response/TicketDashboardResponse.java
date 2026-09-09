package com.nimbusdesk.tickets.dto.response;

import com.nimbusdesk.tickets.model.TicketStatus;
import java.util.List;
import java.util.UUID;

/**
 * Resposta única de {@code GET /bff/v1/tickets/dashboard} - contagem por status, chamados com SLA
 * estourado (contagem + lista) e contagem por categoria (ver TicketDashboardService).
 */
public record TicketDashboardResponse(
    List<PorStatus> porStatus,
    SlaEstourado slaEstourado,
    List<PorCategoria> porCategoria) {

  public record PorStatus(TicketStatus status, long quantidade) {
  }

  /** {@code dataLimiteSla < now() AND status NOT IN (RESOLVIDO,FECHADO,CANCELADO)} - calculado em
   *  leitura, nunca persistido (ver PROJECT_SPEC.md). */
  public record SlaEstourado(long quantidade, List<TicketResponse> tickets) {
  }

  public record PorCategoria(UUID categoriaId, String categoriaNome, long quantidade) {
  }
}
