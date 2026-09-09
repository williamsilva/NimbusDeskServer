package com.nimbusdesk.tickets.controller;

import com.nimbusdesk.common.security.CheckSecurity;
import com.nimbusdesk.tickets.core.EquipamentoRefService;
import com.nimbusdesk.tickets.dto.response.EquipamentoRefOptionResponse;
import com.nimbusdesk.tickets.dto.response.EquipamentoRefResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cópia local somente-leitura do Equipamento do NimbusFlowServer (ver EquipamentoSyncService pra
 *  quem escreve) - sem POST/PUT/DELETE de propósito, EQUIPAMENTO_REF_CONSULT é a única permissão
 *  do módulo (ver PROJECT_SPEC.md). */
@RestController
@RequestMapping("/bff/v1/equipamento-refs")
@RequiredArgsConstructor
public class EquipamentoRefController {

  private final EquipamentoRefService service;

  @GetMapping
  @CheckSecurity.EquipamentoRef.CanConsult
  public List<EquipamentoRefResponse> list() {
    return service.list();
  }

  /** Pro seletor de equipamento no formulário de abertura de chamado - sem gate de permissão. */
  @GetMapping("/options")
  @CheckSecurity.Authenticated
  public List<EquipamentoRefOptionResponse> options() {
    return service.options();
  }
}
