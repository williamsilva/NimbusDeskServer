package com.nimbusdesk.tickets.controller;

import com.nimbusdesk.common.security.CheckSecurity;
import com.nimbusdesk.tickets.core.CategoryService;
import com.nimbusdesk.tickets.dto.request.CategoryRequest;
import com.nimbusdesk.tickets.dto.response.CategoryOptionResponse;
import com.nimbusdesk.tickets.dto.response.CategoryResponse;
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

/** Menu "Configurações &gt; Categorias" - CRUD pequeno, sem paginação. */
@RestController
@RequestMapping("/bff/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryService service;

  @GetMapping
  @CheckSecurity.Category.CanConsult
  public List<CategoryResponse> list() {
    return service.list();
  }

  /** Pro seletor de categoria no formulário de abertura de chamado - sem gate de permissão. */
  @GetMapping("/options")
  @CheckSecurity.Authenticated
  public List<CategoryOptionResponse> options() {
    return service.options();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.Category.CanManage
  public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
    return service.create(request);
  }

  @PutMapping("/{id}")
  @CheckSecurity.Category.CanManage
  public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return service.update(id, request);
  }
}
