package com.nimbusdesk.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

/**
 * Participante do chamado - usuário adicionado (pelo solicitante ou por CHAMADO_MANAGE) pra
 * acompanhar/ver o chamado sem ser o solicitante nem o responsável ("compartilhado com"). Tabela
 * filha (1:N intra-módulo, {@code @ManyToOne} normal - mesma técnica de TicketComment/
 * TicketAttachment). {@code userId} é raw id do NimbusAuth (sem entidade de usuário local, mesmo
 * padrão de {@code Ticket#solicitanteId}); nome resolvido em leitura via UserDirectoryService.
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_participants")
public class TicketParticipant {

  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @Column(name = "user_id", nullable = false, length = 100)
  private String userId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
