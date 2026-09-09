package com.nimbusdesk.tickets.controller;

import com.nimbusdesk.common.security.CheckSecurity;
import com.nimbusdesk.tickets.core.TicketAutomationSettingsService;
import com.nimbusdesk.tickets.dto.request.TicketAutomationSettingsRequest;
import com.nimbusdesk.tickets.dto.response.TicketAutomationSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Menu "Configurações &gt; Automação de chamados" - periodicidade dos 3 jobs pedidos pelo usuário
 *  em 2026-09-09 (ver Javadoc de TicketAutomationSettings). */
@RestController
@RequestMapping("/bff/v1/ticket-automation/settings")
@RequiredArgsConstructor
public class BffTicketAutomationSettingsController {

  private final TicketAutomationSettingsService service;

  @GetMapping
  @CheckSecurity.TicketAutomation.CanConsult
  public TicketAutomationSettingsResponse getSettings() {
    return service.getSettings();
  }

  @PutMapping
  @CheckSecurity.TicketAutomation.CanManage
  public TicketAutomationSettingsResponse updateSettings(@Valid @RequestBody TicketAutomationSettingsRequest request) {
    return service.update(request);
  }
}
