package com.nimbusdesk.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Auditoria explícita de toda transição de status - isso NÃO existe no NimbusFlow (lá o rastro é
 * implícito, só o status atual é guardado); construído do zero aqui porque o PROJECT_SPEC.md pede
 * auditoria explícita pro helpdesk de TI. Uma linha por transição (ver TicketService), nunca
 * editada/removida - por isso sem updatedAt, só um timestamp de ocorrência ({@code changedAt},
 * coluna {@code occurred_at} - nome de coluna evita a palavra reservada "timestamp" do Postgres).
 * {@code statusAnterior} é nulo só pra abertura inicial (ABERTO), que não gera linha de histórico
 * (não há "transição" alguma na criação - ver TicketService#create).
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_status_history")
public class TicketStatusHistory {

  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_anterior", length = 30)
  private TicketStatus statusAnterior;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_novo", nullable = false, length = 30)
  private TicketStatus statusNovo;

  @Column(name = "usuario_id", nullable = false, length = 100)
  private String usuarioId;

  @Column(name = "occurred_at", nullable = false)
  private Instant changedAt;
}
