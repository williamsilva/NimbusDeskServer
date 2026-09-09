package com.nimbusdesk.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Linha única (id sempre 1, {@code CHECK (id = 1)} na migration) - guarda o estado da última
 * tentativa de {@link com.nimbusdesk.tickets.core.EquipamentoSyncService}. {@code
 * lastSuccessStartedAt} é o timestamp de INÍCIO da última chamada bem-sucedida (não o updatedAt
 * mais recente já sincronizado) - usado como cursor da próxima chamada, com uma margem de segurança
 * subtraída (ver EquipamentoSyncService), pra não perder registros em caso de clock skew entre os
 * dois backends.
 */
@Getter
@Setter
@Entity
@Table(name = "equipamento_sync_state")
public class EquipamentoSyncState {

  public static final short SINGLETON_ID = 1;

  @Id
  private Short id = SINGLETON_ID;

  @Column(name = "last_success_started_at")
  private Instant lastSuccessStartedAt;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
