package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.dto.request.SlaRequest;
import com.nimbusdesk.tickets.dto.response.SlaOptionResponse;
import com.nimbusdesk.tickets.dto.response.SlaResponse;
import com.nimbusdesk.tickets.model.Sla;
import com.nimbusdesk.tickets.repository.SlaRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Configurações &gt; SLAs - CRUD simples, sem paginação (mesmo espírito de com.nimbusflow.tickets.
 *  core.DepartmentService). Gate (SLA_CONSULT/SLA_MANAGE) é aplicado declarativamente pelo
 *  controller (ver CheckSecurity.Sla), não aqui. */
@Service
@RequiredArgsConstructor
public class SlaService {

  private final SlaRepository repository;

  @Transactional(readOnly = true)
  public List<SlaResponse> list() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(Sla::getNome, String.CASE_INSENSITIVE_ORDER))
        .map(this::toResponse)
        .toList();
  }

  /** Pro seletor de SLA no formulário de Categoria - sem gate de permissão além de autenticação
   *  (mesmo padrão de CategoryService#options/SetorService#options). */
  @Transactional(readOnly = true)
  public List<SlaOptionResponse> options() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(Sla::getNome, String.CASE_INSENSITIVE_ORDER))
        .map(s -> new SlaOptionResponse(s.getId(), s.getNome()))
        .toList();
  }

  @Transactional
  public SlaResponse create(SlaRequest request) {
    Sla sla = new Sla();
    apply(sla, request);
    return toResponse(repository.save(sla));
  }

  @Transactional
  public SlaResponse update(UUID id, SlaRequest request) {
    Sla sla = getOrThrow(id);
    apply(sla, request);
    return toResponse(repository.save(sla));
  }

  /** Usado por CategoryService (validar existência/derivar prioridadeDefault) e por TicketService
   *  (calcular dataLimiteSla/prioridade na abertura). */
  @Transactional(readOnly = true)
  public Sla getOrThrow(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SLA not found: " + id));
  }

  private void apply(Sla sla, SlaRequest request) {
    sla.setNome(request.nome());
    sla.setTempoRespostaMin(request.tempoRespostaMin());
    sla.setTempoResolucaoMin(request.tempoResolucaoMin());
  }

  private SlaResponse toResponse(Sla sla) {
    return new SlaResponse(
        sla.getId(),
        sla.getNome(),
        sla.getTempoRespostaMin(),
        sla.getTempoResolucaoMin(),
        sla.getCreatedAt(),
        sla.getUpdatedAt());
  }
}
