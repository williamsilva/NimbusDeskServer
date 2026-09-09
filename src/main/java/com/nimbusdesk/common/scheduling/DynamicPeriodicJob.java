package com.nimbusdesk.common.scheduling;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.function.IntSupplier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * Reagendamento em runtime de um job cuja periodicidade (em dias) é configurável via tela
 * "Configurações", sem precisar de redeploy - mesma técnica de com.nimbusflow.works.core.
 * WorkAutoCompleteScheduler (NimbusFlowServer), adaptada pra usar {@link PeriodicTrigger} (a cada N
 * dias, contados a partir do FIM da execução anterior - {@code setFixedRate(false)}) em vez de
 * {@link org.springframework.scheduling.support.CronTrigger} (horário fixo do dia): os jobs de
 * automação de chamados não têm horário configurável nesta fase, só periodicidade em dias (pedido
 * literal do usuário em 2026-09-09).
 *
 * <p>Não é um {@code @Component} - cada job dono (TicketUnassignedAlertScheduler/
 * TicketPendingResponseAlertScheduler/TicketAutoCloseScheduler) instancia o seu próprio, evitando 3
 * classes quase idênticas repetindo a mesma lógica de cancel+reschedule.
 */
public final class DynamicPeriodicJob {

  private final TaskScheduler taskScheduler;
  private final Runnable job;
  private final IntSupplier periodDaysSupplier;
  private final Duration initialDelay;

  private volatile ScheduledFuture<?> scheduledFuture;

  public DynamicPeriodicJob(TaskScheduler taskScheduler, Runnable job, IntSupplier periodDaysSupplier, Duration initialDelay) {
    this.taskScheduler = taskScheduler;
    this.job = job;
    this.periodDaysSupplier = periodDaysSupplier;
    this.initialDelay = initialDelay;
  }

  /** cancel(false): não interrompe uma execução já em andamento - só evita que o trigger ANTIGO
   *  dispare de novo depois (mesmo racional de WorkAutoCompleteScheduler#reschedule). A PRIMEIRA
   *  chamada (boot) usa {@code initialDelay}; reagendamentos seguintes (settings mudou) também -
   *  simplicidade: não vale a pena tentar preservar "quanto já tinha se passado" da contagem
   *  anterior. */
  public synchronized void reschedule() {
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
    }

    PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofDays(periodDaysSupplier.getAsInt()));
    trigger.setFixedRate(false);
    trigger.setInitialDelay(initialDelay);

    scheduledFuture = taskScheduler.schedule(job, trigger);
  }
}
