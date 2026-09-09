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
 * Acordo de nível de serviço - Configurações &gt; SLAs. {@code tempoResolucaoMin} é o único usado
 * hoje pra calcular {@code Ticket.dataLimiteSla} na abertura (dataAbertura + tempoResolucaoMin);
 * {@code tempoRespostaMin} é guardado pra uso futuro (indicador de 1ª resposta), sem cálculo
 * automático nesta fase (fora de escopo, ver PROJECT_SPEC.md). {@code prioridadeDefault} existiu
 * até 2026-09-09, quando foi removido: a prioridade passou a ser sempre informada pelo solicitante
 * na abertura do chamado (ver TicketRequest#prioridade/TicketService#create), então o default por
 * SLA ficou sem uso.
 */
@Getter
@Setter
@Entity
@Table(name = "slas")
public class Sla {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, length = 120)
  private String nome;

  @Column(name = "tempo_resposta_min", nullable = false)
  private Integer tempoRespostaMin;

  @Column(name = "tempo_resolucao_min", nullable = false)
  private Integer tempoResolucaoMin;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
