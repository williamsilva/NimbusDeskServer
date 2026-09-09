package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.dto.response.EquipamentoRefOptionResponse;
import com.nimbusdesk.tickets.dto.response.EquipamentoRefResponse;
import com.nimbusdesk.tickets.model.EquipamentoRef;
import com.nimbusdesk.tickets.repository.EquipamentoRefRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cópia local somente-leitura pelo app (ver EquipamentoSyncService pra quem escreve). Gate
 *  (EQUIPAMENTO_REF_CONSULT) é aplicado declarativamente pelo controller em {@link #list()};
 *  {@link #options()} não tem gate - alimenta o seletor de equipamento no formulário de abertura de
 *  chamado. */
@Service
@RequiredArgsConstructor
public class EquipamentoRefService {

  private final EquipamentoRefRepository repository;

  @Transactional(readOnly = true)
  public List<EquipamentoRefResponse> list() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(EquipamentoRef::getCodigo, String.CASE_INSENSITIVE_ORDER))
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EquipamentoRefOptionResponse> options() {
    return repository.findAll().stream()
        .map(e -> new EquipamentoRefOptionResponse(e.getId(), e.getCodigo(), e.getNome()))
        .sorted(Comparator.comparing(EquipamentoRefOptionResponse::codigo, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private EquipamentoRefResponse toResponse(EquipamentoRef e) {
    return new EquipamentoRefResponse(
        e.getId(), e.getCodigo(), e.getNome(), e.getLocalizacao(), e.getStatusOperacional(),
        e.getAtualizadoEm(), e.getSincronizadoEm());
  }
}
