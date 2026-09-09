package com.nimbusdesk.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Linha única (mesmo padrão de {@code Sla}/{@code EmailSettingsEntity}) - periodicidade dos 3 jobs
 * de automação de chamados pedidos pelo usuário em 2026-09-09, editável via tela "Configurações"
 * sem precisar de redeploy (ver TicketAutomationSettingsService, que reagenda os 3 jobs em runtime
 * quando esta linha muda - com.nimbusdesk.tickets.core.TicketUnassignedAlertScheduler/
 * TicketPendingResponseAlertScheduler/TicketAutoCloseScheduler):
 *
 * <ul>
 *   <li>{@code unassignedAlertPeriodicidadeDias} - a cada quantos dias o e-mail de "chamados
 *       ABERTO sem responsável" é reenviado pro T.I. (ver TicketUnassignedAlertJob).</li>
 *   <li>{@code pendingResponseAlertPeriodicidadeDias} - a cada quantos dias o e-mail de "chamado
 *       aguardando SUA resposta" é reenviado pro solicitante/compartilhados ou responsável (ver
 *       TicketPendingResponseAlertJob).</li>
 *   <li>{@code autoClosePeriodicidadeDias} - a cada quantos dias o job de auto-fechamento roda (ver
 *       TicketAutoCloseJob).</li>
 *   <li>{@code autoCloseCarenciaDias} - quantos dias um chamado precisa estar parado em RESOLVIDO
 *       antes do job de auto-fechamento fechá-lo (evita fechar um chamado resolvido há 1 minuto -
 *       decisão do usuário via AskUserQuestion em 2026-09-09).</li>
 * </ul>
 *
 * As 3 automações ficam numa única tabela (em vez de 3 tabelas separadas, uma por job) porque são
 * facetas do MESMO concern ("periodicidade das automações de chamado", todas pedidas juntas na
 * mesma frase pelo usuário) - diferente do precedente observado no NimbusFlowServer (email_settings
 * x work_auto_complete_settings, que são concerns de fato distintos e ficaram em tabelas
 * separadas).
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_automation_settings")
public class TicketAutomationSettings {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(name = "unassigned_alert_periodicidade_dias", nullable = false)
  private Integer unassignedAlertPeriodicidadeDias;

  @Column(name = "pending_response_alert_periodicidade_dias", nullable = false)
  private Integer pendingResponseAlertPeriodicidadeDias;

  @Column(name = "auto_close_periodicidade_dias", nullable = false)
  private Integer autoClosePeriodicidadeDias;

  @Column(name = "auto_close_carencia_dias", nullable = false)
  private Integer autoCloseCarenciaDias;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
