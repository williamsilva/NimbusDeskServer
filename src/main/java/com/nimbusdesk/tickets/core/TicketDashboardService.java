package com.nimbusdesk.tickets.core;

import com.nimbusdesk.common.security.NdSecurity;
import com.nimbusdesk.tickets.dto.response.TicketDashboardResponse;
import com.nimbusdesk.tickets.dto.response.TicketResponse;
import com.nimbusdesk.tickets.model.TicketStatus;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Indicadores do módulo Chamados - GET /bff/v1/tickets/dashboard, exige CHAMADO_CONSULT. Mesmo
 * estilo de com.nimbusflow.patrimonio.core.PatrimonioDashboardService: busca tudo já resolvido (via
 * {@link TicketService#allAsResponses}) e agrupa em memória - mesmo racional de volume pequeno já
 * usado em todo o resto do NimbusFlow/NimbusDesk pra telas de indicadores.
 */
@Service
@RequiredArgsConstructor
public class TicketDashboardService {

  private final TicketService ticketService;
  private final NdSecurity ndSecurity;

  @Transactional(readOnly = true)
  public TicketDashboardResponse dashboard() {
    if (!ndSecurity.canConsultChamados()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing CHAMADO_CONSULT authority");
    }

    List<TicketResponse> all = ticketService.allAsResponses();

    return new TicketDashboardResponse(porStatus(all), slaEstourado(all), porCategoria(all));
  }

  private List<TicketDashboardResponse.PorStatus> porStatus(List<TicketResponse> all) {
    Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
    all.forEach(t -> counts.merge(t.status(), 1L, Long::sum));

    return Arrays.stream(TicketStatus.values())
        .map(s -> new TicketDashboardResponse.PorStatus(s, counts.getOrDefault(s, 0L)))
        .toList();
  }

  /** {@code dataLimiteSla < now() AND status NOT IN (RESOLVIDO,FECHADO,CANCELADO)} - já calculado
   *  em TicketResponse#slaEstourado (ver TicketService#toResponse), aqui só filtramos. */
  private TicketDashboardResponse.SlaEstourado slaEstourado(List<TicketResponse> all) {
    List<TicketResponse> estourados = all.stream().filter(TicketResponse::slaEstourado).toList();
    return new TicketDashboardResponse.SlaEstourado(estourados.size(), estourados);
  }

  private List<TicketDashboardResponse.PorCategoria> porCategoria(List<TicketResponse> all) {
    Map<UUID, Long> counts = new LinkedHashMap<>();
    Map<UUID, String> names = new LinkedHashMap<>();
    for (TicketResponse t : all) {
      if (t.categoriaId() == null) {
        continue;
      }
      counts.merge(t.categoriaId(), 1L, Long::sum);
      names.putIfAbsent(t.categoriaId(), t.categoriaNome());
    }

    return counts.entrySet().stream()
        .map(e -> new TicketDashboardResponse.PorCategoria(e.getKey(), names.get(e.getKey()), e.getValue()))
        .sorted(Comparator.comparing(TicketDashboardResponse.PorCategoria::quantidade).reversed())
        .toList();
  }
}
