package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.dto.request.CategoryRequest;
import com.nimbusdesk.tickets.dto.response.CategoryOptionResponse;
import com.nimbusdesk.tickets.dto.response.CategoryResponse;
import com.nimbusdesk.tickets.model.Category;
import com.nimbusdesk.tickets.model.Sla;
import com.nimbusdesk.tickets.repository.CategoryRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Configurações &gt; Categorias - CRUD simples, sem paginação. {@link #options()} não tem gate -
 *  alimenta o seletor de categoria no formulário de abertura de chamado, que qualquer usuário
 *  autenticado pode abrir (mesmo espírito de com.nimbusflow.tickets.core.DepartmentService#options). */
@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository repository;
  private final SlaService slaService;

  @Transactional(readOnly = true)
  public List<CategoryResponse> list() {
    List<Category> categories = repository.findAll();
    Map<UUID, String> slaNamesById = resolveSlaNames(categories.stream().map(Category::getSlaId));

    return categories.stream()
        .sorted(Comparator.comparing(Category::getNome, String.CASE_INSENSITIVE_ORDER))
        .map(c -> toResponse(c, slaNamesById.get(c.getSlaId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CategoryOptionResponse> options() {
    return repository.findAll().stream()
        .filter(Category::isAtivo)
        .map(c -> new CategoryOptionResponse(c.getId(), c.getNome()))
        .sorted(Comparator.comparing(CategoryOptionResponse::nome, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Transactional
  public CategoryResponse create(CategoryRequest request) {
    Category category = new Category();
    apply(category, request);
    category = repository.save(category);
    return toResponse(category, slaService.getOrThrow(category.getSlaId()).getNome());
  }

  @Transactional
  public CategoryResponse update(UUID id, CategoryRequest request) {
    Category category = getOrThrow(id);
    apply(category, request);
    category = repository.save(category);
    return toResponse(category, slaService.getOrThrow(category.getSlaId()).getNome());
  }

  /** Usado por TicketService pra validar existência/derivar slaId na abertura de um chamado. */
  @Transactional(readOnly = true)
  public Category getOrThrow(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + id));
  }

  private void apply(Category category, CategoryRequest request) {
    // Falha cedo (400) se o SLA não existir, em vez de deixar a FK do banco estourar como 500
    // genérico na hora do INSERT/UPDATE.
    slaService.getOrThrow(request.slaId());

    category.setNome(request.nome());
    category.setSlaId(request.slaId());
    category.setAtivo(request.ativo());
  }

  private Map<UUID, String> resolveSlaNames(java.util.stream.Stream<UUID> slaIds) {
    Set<UUID> ids = slaIds.collect(Collectors.toSet());
    return ids.stream().collect(Collectors.toMap(id -> id, id -> slaService.getOrThrow(id).getNome()));
  }

  private CategoryResponse toResponse(Category category, String slaNome) {
    return new CategoryResponse(
        category.getId(),
        category.getNome(),
        category.getSlaId(),
        slaNome,
        category.isAtivo(),
        category.getCreatedAt(),
        category.getUpdatedAt());
  }
}
