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
 * Categoria do chamado - Configurações &gt; Categorias. {@code slaId} é uma coluna crua com FK de
 * banco (não {@code @ManyToOne}) - mesma técnica de referência intra-módulo já usada em todo o
 * NimbusFlow entre agregados que precisam ficar desacoplados (aqui, Category e Sla são duas
 * entidades-configuração independentes, resolvidas junto via SlaRepository quando o CategoryService
 * precisa exibir o nome do SLA). {@code slaId} de um chamado é sempre copiado de
 * {@code categoria.slaId} na abertura - nunca setado manualmente (ver PROJECT_SPEC.md).
 */
@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, length = 120)
  private String nome;

  @Column(name = "sla_id", nullable = false)
  private UUID slaId;

  @Column(nullable = false)
  private boolean ativo = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
