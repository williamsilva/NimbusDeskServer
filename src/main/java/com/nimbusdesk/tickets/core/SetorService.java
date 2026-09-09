package com.nimbusdesk.tickets.core;

import com.nimbussystems.commons.security.UserDirectoryService;
import com.nimbussystems.commons.security.UserMinimalResponse;
import com.nimbusdesk.tickets.dto.request.SetorRequest;
import com.nimbusdesk.tickets.dto.response.SetorOptionResponse;
import com.nimbusdesk.tickets.dto.response.SetorResponse;
import com.nimbusdesk.tickets.model.Setor;
import com.nimbusdesk.tickets.repository.SetorRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Configurações &gt; Setores - mesmo desenho de com.nimbusflow.tickets.core.DepartmentService.
 *  {@link #options()} não tem gate - alimenta o seletor de setor no formulário de abertura de
 *  chamado. */
@Service
@RequiredArgsConstructor
public class SetorService {

  private final SetorRepository repository;
  private final UserDirectoryService userDirectoryService;

  @Transactional(readOnly = true)
  public List<SetorResponse> list() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(Setor::getNome, String.CASE_INSENSITIVE_ORDER))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<SetorOptionResponse> options() {
    return repository.findAll().stream()
        .filter(Setor::isAtivo)
        .map(s -> new SetorOptionResponse(s.getId(), s.getNome()))
        .sorted(Comparator.comparing(SetorOptionResponse::nome, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Transactional
  public SetorResponse create(SetorRequest request) {
    Setor setor = new Setor();
    apply(setor, request);
    return toResponse(repository.save(setor));
  }

  @Transactional
  public SetorResponse update(UUID id, SetorRequest request) {
    Setor setor = getOrThrow(id);
    apply(setor, request);
    return toResponse(repository.save(setor));
  }

  /** Usado por TicketService pra validar existência de setorId na abertura/edição de um chamado. */
  @Transactional(readOnly = true)
  public boolean exists(UUID id) {
    return repository.existsById(id);
  }

  private Setor getOrThrow(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Setor not found: " + id));
  }

  private void apply(Setor setor, SetorRequest request) {
    setor.setNome(request.nome());
    setor.setUserIds(new HashSet<>(request.userIds()));
    setor.setAtivo(request.ativo());
  }

  private SetorResponse toResponse(Setor setor) {
    return new SetorResponse(
        setor.getId(),
        setor.getNome(),
        setor.getUserIds(),
        resolveUsers(setor.getUserIds()),
        setor.isAtivo(),
        setor.getCreatedAt(),
        setor.getUpdatedAt());
  }

  /** Resolvido ao vivo via UserDirectoryService, id por id - mesmo racional de
   *  com.nimbusflow.tickets.core.DepartmentService#resolveUsers. */
  private List<UserMinimalResponse> resolveUsers(Set<String> userIds) {
    return userIds.stream()
        .map(this::resolveUser)
        .sorted(Comparator.comparing(u -> u.name() == null ? "" : u.name(), String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private UserMinimalResponse resolveUser(String userId) {
    UUID uuid = UserDirectoryService.parseIdOrNull(userId);
    return userDirectoryService.summaryFor(uuid).orElse(new UserMinimalResponse(uuid, userId, null));
  }
}
