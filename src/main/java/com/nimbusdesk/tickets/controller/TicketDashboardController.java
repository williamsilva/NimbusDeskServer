package com.nimbusdesk.tickets.controller;

import com.nimbusdesk.tickets.core.TicketDashboardService;
import com.nimbusdesk.tickets.dto.response.TicketDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /bff/v1/tickets/dashboard} - controller separado de {@link TicketController} (mesmo
 * padrão de com.nimbusflow.patrimonio.controller.PatrimonioDashboardController), mas mapeado sob o
 * MESMO prefixo "/bff/v1/tickets": o path literal "/dashboard" tem precedência sobre o padrão de
 * variável "/{id}" de TicketController na resolução de rotas do Spring MVC (PatternsRequestCondition
 * ordena padrões literais antes de padrões com variável), então não há ambiguidade real em runtime -
 * uma requisição GET /bff/v1/tickets/dashboard nunca cai em getById(id="dashboard").
 */
@RestController
@RequestMapping("/bff/v1/tickets/dashboard")
@RequiredArgsConstructor
public class TicketDashboardController {

  private final TicketDashboardService service;

  @GetMapping
  public TicketDashboardResponse dashboard() {
    return service.dashboard();
  }
}
