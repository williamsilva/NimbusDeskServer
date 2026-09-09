package com.nimbusdesk.tickets.core;

import com.nimbusdesk.common.security.NimbusFlowInternalClient;
import com.nimbusdesk.common.security.NimbusFlowInternalClient.EquipamentoDto;
import com.nimbusdesk.tickets.model.EquipamentoRef;
import com.nimbusdesk.tickets.model.EquipamentoSyncState;
import com.nimbusdesk.tickets.repository.EquipamentoRefRepository;
import com.nimbusdesk.tickets.repository.EquipamentoSyncStateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job de sincronização de equipamentos com o NimbusFlowServer (ver PROJECT_SPEC.md "Integração com
 * NimbusFlow"). Roda a cada {@code nimbus.equipamento-sync.interval-ms} (default 20 min), busca
 * equipamentos atualizados desde o cursor guardado em {@link EquipamentoSyncState} e faz upsert em
 * {@link EquipamentoRef} por id.
 *
 * <p><b>Cursor</b>: o timestamp guardado é o INÍCIO da última chamada bem-sucedida (não o {@code
 * updatedAt} mais recente já sincronizado) - evita perder registros em caso de clock skew entre os
 * dois backends. A próxima chamada usa esse valor menos {@link #SAFETY_MARGIN} (mesma margem de
 * segurança). Sem execução prévia bem-sucedida, o cursor é {@link Instant#EPOCH} (busca tudo).
 *
 * <p><b>Resiliente por completo</b>: qualquer falha (rede, timeout, o endpoint ainda não existir no
 * NimbusFlowServer - 404 -, 4xx/5xx) é capturada aqui, logada e contabilizada num contador em
 * memória (sem Micrometer novo, conforme PROJECT_SPEC.md) - NUNCA propagada. Deixar uma exceção
 * escapar de um método {@code @Scheduled} faz o Spring parar de reagendar essa tarefa pro resto do
 * uptime do processo (ver ScheduledMethodRunnable) - o sistema precisa continuar tentando na
 * próxima janela, com a última cópia local intacta.
 */
@Slf4j
@Service
public class EquipamentoSyncService {

  private static final Duration SAFETY_MARGIN = Duration.ofMinutes(5);

  private final EquipamentoRefRepository equipamentoRefRepository;
  private final EquipamentoSyncStateRepository stateRepository;
  private final NimbusFlowInternalClient client;

  /** Contador simples em memória (reseta a cada restart) - só pra observabilidade via log/log
   *  aggregator, não exposto por endpoint nenhum nesta fase. */
  private final AtomicInteger consecutiveFailures = new AtomicInteger();

  public EquipamentoSyncService(
      EquipamentoRefRepository equipamentoRefRepository,
      EquipamentoSyncStateRepository stateRepository,
      NimbusFlowInternalClient client) {
    this.equipamentoRefRepository = equipamentoRefRepository;
    this.stateRepository = stateRepository;
    this.client = client;
  }

  @Scheduled(
      fixedDelayString = "${nimbus.equipamento-sync.interval-ms:1200000}",
      initialDelayString = "${nimbus.equipamento-sync.initial-delay-ms:30000}")
  @Transactional
  public void sync() {
    Instant attemptStartedAt = Instant.now();
    EquipamentoSyncState state = stateRepository.findById(EquipamentoSyncState.SINGLETON_ID)
        .orElseGet(() -> {
          EquipamentoSyncState fresh = new EquipamentoSyncState();
          fresh.setId(EquipamentoSyncState.SINGLETON_ID);
          return fresh;
        });

    Instant cursor = state.getLastSuccessStartedAt() == null
        ? Instant.EPOCH
        : state.getLastSuccessStartedAt().minus(SAFETY_MARGIN);

    try {
      List<EquipamentoDto> items = client.fetchEquipamentosUpdatedAfter(cursor);
      items.forEach(this::upsert);

      state.setLastSuccessStartedAt(attemptStartedAt);
      state.setLastAttemptAt(attemptStartedAt);
      state.setLastError(null);
      stateRepository.save(state);

      consecutiveFailures.set(0);
      log.info("Sincronização de equipamentos concluída: {} registro(s) processado(s) (cursor={})",
          items.size(), cursor);
    } catch (Exception e) {
      int failures = consecutiveFailures.incrementAndGet();
      state.setLastAttemptAt(attemptStartedAt);
      state.setLastError(e.getMessage());
      stateRepository.save(state);
      log.error(
          "Falha na sincronização de equipamentos com o NimbusFlowServer (tentativa consecutiva "
              + "com falha #{}) - mantendo a última cópia local, nova tentativa na próxima janela: {}",
          failures, e.getMessage(), e);
    }
  }

  private void upsert(EquipamentoDto dto) {
    if (dto.id() == null) {
      log.warn("Ignorando registro de equipamento sem id vindo do NimbusFlowServer: {}", dto);
      return;
    }

    EquipamentoRef local = equipamentoRefRepository.findById(dto.id()).orElseGet(EquipamentoRef::new);
    local.setId(dto.id());
    local.setCodigo(dto.numeroPatrimonio());
    local.setNome(dto.descricao());
    local.setLocalizacao(dto.localizacaoAtual());
    local.setStatusOperacional(dto.statusOperacional());
    local.setAtualizadoEm(dto.atualizadoEm());
    local.setSincronizadoEm(Instant.now());
    equipamentoRefRepository.save(local);
  }
}
