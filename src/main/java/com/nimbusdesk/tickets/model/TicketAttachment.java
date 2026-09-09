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
 * Anexo do chamado - tabela filha (1:N), mesma técnica de {@link TicketComment}. Guarda só a key do
 * objeto no bucket MinIO ({@code storageKey}) - a URL assinada é gerada sob demanda (TTL curto, ver
 * com.nimbusdesk.common.storage.StorageService#presignedGetUrl), nunca persistida.
 *
 * <p>{@code comment} (nullable, 2026-09-08): anexo enviado junto de um comentário (ver
 * TicketCommentService#add) - aparece na timeline junto da mensagem, não numa lista solta. Anexos
 * da ABERTURA do chamado (enviados junto com {@code POST /bff/v1/tickets}) não têm comentário -
 * fazem parte da descrição inicial, não de uma resposta.
 */
@Getter
@Setter
@Entity
@Table(name = "ticket_attachments")
public class TicketAttachment {

  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private Ticket ticket;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "comment_id")
  private TicketComment comment;

  @Column(name = "nome_arquivo", nullable = false, length = 300)
  private String nomeArquivo;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "tipo_mime", length = 100)
  private String tipoMime;

  @Column(name = "tamanho_bytes")
  private Long tamanhoBytes;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
