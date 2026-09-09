package com.nimbusdesk.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@link TaskScheduler} dedicado aos jobs de automação de chamados reagendados em runtime (ver
 * com.nimbusdesk.common.scheduling.DynamicPeriodicJob) - {@code @EnableScheduling} (já presente em
 * NimbusDeskApplication) cobre {@code @Scheduled} estático (ex.: EquipamentoSyncService,
 * TicketEventBroadcaster#heartbeat), mas o reagendamento manual via {@code TaskScheduler#schedule}
 * precisa de um bean explícito - mesmo padrão de com.nimbusflow.common.config.SchedulingConfig
 * (NimbusFlowServer). Pool de 3 threads (um por job de automação) - nenhum é CPU-bound nem demorado,
 * mas isolar evita que um job travado atrase o disparo dos outros dois.
 */
@Configuration
public class SchedulingConfig {

  @Bean
  public TaskScheduler ticketAutomationTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(3);
    scheduler.setThreadNamePrefix("nimbusdesk-scheduler-");
    scheduler.initialize();
    return scheduler;
  }
}
