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
 * Reagendamento em runtime do {@link TicketPendingResponseAlertJob} - ver Javadoc de
 * {@link TicketUnassignedAlertScheduler} (mesma técnica).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketPendingResponseAlertScheduler {

  private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);

  private final TaskScheduler ticketAutomationTaskScheduler;
  private final TicketPendingResponseAlertJob job;
  private final TicketAutomationSettingsService settingsService;

  private DynamicPeriodicJob dynamicJob;

  @PostConstruct
  void init() {
    dynamicJob = new DynamicPeriodicJob(
        ticketAutomationTaskScheduler, job::run, settingsService::getPendingResponseAlertPeriodicidadeDias, INITIAL_DELAY);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    dynamicJob.reschedule();
    log.info("TicketPendingResponseAlertJob agendado a cada {} dia(s)", settingsService.getPendingResponseAlertPeriodicidadeDias());
  }

  @EventListener(TicketAutomationSettingsChangedEvent.class)
  public void onSettingsChanged(TicketAutomationSettingsChangedEvent event) {
    dynamicJob.reschedule();
    log.info("TicketPendingResponseAlertJob reagendado a cada {} dia(s)", settingsService.getPendingResponseAlertPeriodicidadeDias());
  }
}
