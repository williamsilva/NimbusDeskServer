package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.dto.request.TicketAutomationSettingsRequest;
import com.nimbusdesk.tickets.dto.response.TicketAutomationSettingsResponse;
import com.nimbusdesk.tickets.model.TicketAutomationSettings;
import com.nimbusdesk.tickets.repository.TicketAutomationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuração dos 3 jobs de automação de chamados (ver Javadoc de {@link TicketAutomationSettings})
 * - mesmo padrão de linha única de com.nimbusflow.works.core.WorkAutoCompleteSettingsService
 * (NimbusFlowServer), sem fallback pra application.yml (o banco é a única fonte). Antes do primeiro
 * {@link #update}, os defaults abaixo valem: 1 dia pros 2 alertas por e-mail, 1 dia de periodicidade
 * de execução + 5 dias de carência pro auto-fechamento.
 *
 * <p>Os getters individuais são {@code @Cacheable} (chamados a cada reagendamento dos schedulers e,
 * no caso de {@link #getAutoCloseCarenciaDias()}, a cada execução do job de auto-fechamento) - cache
 * invalidado inteiro no {@link #update}.
 */
@Service
@RequiredArgsConstructor
public class TicketAutomationSettingsService {

  private static final String CACHE = "nimbusdesk-ticket-automation-settings";
  private static final int DEFAULT_UNASSIGNED_ALERT_PERIODICIDADE_DIAS = 1;
  private static final int DEFAULT_PENDING_RESPONSE_ALERT_PERIODICIDADE_DIAS = 1;
  private static final int DEFAULT_AUTO_CLOSE_PERIODICIDADE_DIAS = 1;
  private static final int DEFAULT_AUTO_CLOSE_CARENCIA_DIAS = 5;

  private final TicketAutomationSettingsRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  @Cacheable(value = CACHE, key = "'unassignedAlertPeriodicidadeDias'")
  @Transactional(readOnly = true)
  public int getUnassignedAlertPeriodicidadeDias() {
    return repository.findFirstBy()
        .map(TicketAutomationSettings::getUnassignedAlertPeriodicidadeDias)
        .orElse(DEFAULT_UNASSIGNED_ALERT_PERIODICIDADE_DIAS);
  }

  @Cacheable(value = CACHE, key = "'pendingResponseAlertPeriodicidadeDias'")
  @Transactional(readOnly = true)
  public int getPendingResponseAlertPeriodicidadeDias() {
    return repository.findFirstBy()
        .map(TicketAutomationSettings::getPendingResponseAlertPeriodicidadeDias)
        .orElse(DEFAULT_PENDING_RESPONSE_ALERT_PERIODICIDADE_DIAS);
  }

  @Cacheable(value = CACHE, key = "'autoClosePeriodicidadeDias'")
  @Transactional(readOnly = true)
  public int getAutoClosePeriodicidadeDias() {
    return repository.findFirstBy()
        .map(TicketAutomationSettings::getAutoClosePeriodicidadeDias)
        .orElse(DEFAULT_AUTO_CLOSE_PERIODICIDADE_DIAS);
  }

  @Cacheable(value = CACHE, key = "'autoCloseCarenciaDias'")
  @Transactional(readOnly = true)
  public int getAutoCloseCarenciaDias() {
    return repository.findFirstBy()
        .map(TicketAutomationSettings::getAutoCloseCarenciaDias)
        .orElse(DEFAULT_AUTO_CLOSE_CARENCIA_DIAS);
  }

  @Transactional(readOnly = true)
  public TicketAutomationSettingsResponse getSettings() {
    return repository.findFirstBy().map(this::toResponse).orElseGet(() -> new TicketAutomationSettingsResponse(
        DEFAULT_UNASSIGNED_ALERT_PERIODICIDADE_DIAS,
        DEFAULT_PENDING_RESPONSE_ALERT_PERIODICIDADE_DIAS,
        DEFAULT_AUTO_CLOSE_PERIODICIDADE_DIAS,
        DEFAULT_AUTO_CLOSE_CARENCIA_DIAS,
        null));
  }

  /** Salva e dispara {@link TicketAutomationSettingsChangedEvent} - os 3 schedulers reagendam os
   *  jobs em runtime a partir dele (periodicidade só é lida aqui, no boot, e a cada reagendamento -
   *  nunca numa anotação {@code @Scheduled} estática, é isso que permite mudar sem redeploy). */
  @CacheEvict(value = CACHE, allEntries = true)
  @Transactional
  public TicketAutomationSettingsResponse update(TicketAutomationSettingsRequest request) {
    TicketAutomationSettings settings = repository.findFirstBy().orElseGet(TicketAutomationSettings::new);
    settings.setUnassignedAlertPeriodicidadeDias(request.unassignedAlertPeriodicidadeDias());
    settings.setPendingResponseAlertPeriodicidadeDias(request.pendingResponseAlertPeriodicidadeDias());
    settings.setAutoClosePeriodicidadeDias(request.autoClosePeriodicidadeDias());
    settings.setAutoCloseCarenciaDias(request.autoCloseCarenciaDias());
    settings = repository.save(settings);

    eventPublisher.publishEvent(new TicketAutomationSettingsChangedEvent());

    return toResponse(settings);
  }

  private TicketAutomationSettingsResponse toResponse(TicketAutomationSettings settings) {
    return new TicketAutomationSettingsResponse(
        settings.getUnassignedAlertPeriodicidadeDias(),
        settings.getPendingResponseAlertPeriodicidadeDias(),
        settings.getAutoClosePeriodicidadeDias(),
        settings.getAutoCloseCarenciaDias(),
        settings.getUpdatedAt());
  }
}
