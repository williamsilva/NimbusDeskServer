package com.nimbusdesk.tickets.controller;

import com.nimbusdesk.common.security.CheckSecurity;
import com.nimbusdesk.tickets.core.SetorService;
import com.nimbusdesk.tickets.dto.request.SetorRequest;
import com.nimbusdesk.tickets.dto.response.SetorOptionResponse;
import com.nimbusdesk.tickets.dto.response.SetorResponse;
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

/** Menu "Configurações &gt; Setores" - CRUD pequeno, sem paginação (mesmo padrão de
 *  com.nimbusflow.tickets.controller.DepartmentController). */
@RestController
@RequestMapping("/bff/v1/setores")
@RequiredArgsConstructor
public class SetorController {

  private final SetorService service;

  @GetMapping
  @CheckSecurity.Setor.CanConsult
  public List<SetorResponse> list() {
    return service.list();
  }

  /** Pro seletor de setor no formulário de abertura de chamado - sem gate de permissão. */
  @GetMapping("/options")
  @CheckSecurity.Authenticated
  public List<SetorOptionResponse> options() {
    return service.options();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.Setor.CanManage
  public SetorResponse create(@Valid @RequestBody SetorRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @CheckSecurity.Setor.CanManage
  public SetorResponse update(@PathVariable UUID id, @Valid @RequestBody SetorRequest request) {
    return service.update(id, request);
  }
}
