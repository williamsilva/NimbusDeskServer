package com.nimbusdesk.tickets.controller;

import com.nimbusdesk.common.security.CheckSecurity;
import com.nimbusdesk.tickets.core.SlaService;
import com.nimbusdesk.tickets.dto.request.SlaRequest;
import com.nimbusdesk.tickets.dto.response.SlaOptionResponse;
import com.nimbusdesk.tickets.dto.response.SlaResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Menu "Configurações &gt; SLAs" - CRUD pequeno, sem paginação. */
@RestController
@RequestMapping("/bff/v1/slas")
@RequiredArgsConstructor
public class SlaController {

  private final SlaService service;

  @GetMapping
  @CheckSecurity.Sla.CanConsult
  public List<SlaResponse> list() {
    return service.list();
  }

  /** Pro seletor de SLA no formulário de Categoria (Configurações > Categorias) - sem gate de
   *  permissão além de autenticação, mesmo padrão de CategoryController/SetorController#options. */
  @GetMapping("/options")
  @CheckSecurity.Authenticated
  public List<SlaOptionResponse> options() {
    return service.options();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.Sla.CanManage
  public SlaResponse create(@Valid @RequestBody SlaRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @CheckSecurity.Sla.CanManage
  public SlaResponse update(@PathVariable UUID id, @Valid @RequestBody SlaRequest request) {
    return service.update(id, request);
  }
}
