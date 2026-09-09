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
 * Comentário do chamado - tabela filha (1:N), intra-módulo (Ticket já vive em tickets, sem cruzar
 * pacote-raiz nenhum), por isso usa {@code @ManyToOne} normal - mesma técnica de
 * com.nimbusflow.tickets.model.TicketClosePhoto. {@code interno=true} só é visível pra quem tem
 * CHAMADO_MANAGE (ver TicketCommentService); {@code autorNome} nunca é persistido, é resolvido em
 * leitura via UserDirectoryService (ver TicketCommentResponse).
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_comments")
public class TicketComment {

  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @Column(name = "autor_id", nullable = false, length = 100)
  private String autorId;

  @Column(nullable = false, length = 4000)
  private String mensagem;

  @Column(nullable = false)
  private boolean interno;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
