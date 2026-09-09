package com.nimbusdesk.tickets.core;

import com.nimbusdesk.common.scheduling.DynamicPeriodicJob;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Reagendamento em runtime do {@link TicketUnassignedAlertJob} - diferente de um
 * {@code @Scheduled(fixedDelay = ...)} estático, o trigger é reconstruído sempre que a periodicidade
 * muda pela tela "Configurações" (ver TicketAutomationSettingsService/
 * TicketAutomationSettingsChangedEvent) - é isso que permite mudar sem redeploy (ver
 * com.nimbusdesk.common.scheduling.DynamicPeriodicJob e o precedente arquitetural de
 * com.nimbusflow.works.core.WorkAutoCompleteScheduler no NimbusFlowServer).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketUnassignedAlertScheduler {

  private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);

  private final TaskScheduler ticketAutomationTaskScheduler;
  private final TicketUnassignedAlertJob job;
  private final TicketAutomationSettingsService settingsService;

  private DynamicPeriodicJob dynamicJob;

  @PostConstruct
  void init() {
    dynamicJob = new DynamicPeriodicJob(
        ticketAutomationTaskScheduler, job::run, settingsService::getUnassignedAlertPeriodicidadeDias, INITIAL_DELAY);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    dynamicJob.reschedule();
    log.info("TicketUnassignedAlertJob agendado a cada {} dia(s)", settingsService.getUnassignedAlertPeriodicidadeDias());
  }

  @EventListener(TicketAutomationSettingsChangedEvent.class)
  public void onSettingsChanged(TicketAutomationSettingsChangedEvent event) {
    dynamicJob.reschedule();
    log.info("TicketUnassignedAlertJob reagendado a cada {} dia(s)", settingsService.getUnassignedAlertPeriodicidadeDias());
  }
}
