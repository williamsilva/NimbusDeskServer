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
 * Reagendamento em runtime do {@link TicketAutoCloseJob} - ver Javadoc de
 * {@link TicketUnassignedAlertScheduler} (mesma técnica). A carência (dias em RESOLVIDO antes de
 * fechar) é lida direto pelo job (ver TicketAutoCloseJob#run/TicketAutomationSettingsService#
 * getAutoCloseCarenciaDias) - só a PERIODICIDADE DE EXECUÇÃO passa por aqui.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketAutoCloseScheduler {

  private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);

  private final TaskScheduler ticketAutomationTaskScheduler;
  private final TicketAutoCloseJob job;
  private final TicketAutomationSettingsService settingsService;

  private DynamicPeriodicJob dynamicJob;

  @PostConstruct
  void init() {
    dynamicJob = new DynamicPeriodicJob(
        ticketAutomationTaskScheduler, job::run, settingsService::getAutoClosePeriodicidadeDias, INITIAL_DELAY);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    dynamicJob.reschedule();
    log.info("TicketAutoCloseJob agendado a cada {} dia(s) (carência: {} dia(s))",
        settingsService.getAutoClosePeriodicidadeDias(), settingsService.getAutoCloseCarenciaDias());
  }

  @EventListener(TicketAutomationSettingsChangedEvent.class)
  public void onSettingsChanged(TicketAutomationSettingsChangedEvent event) {
    dynamicJob.reschedule();
    log.info("TicketAutoCloseJob reagendado a cada {} dia(s) (carência: {} dia(s))",
        settingsService.getAutoClosePeriodicidadeDias(), settingsService.getAutoCloseCarenciaDias());
  }
}
