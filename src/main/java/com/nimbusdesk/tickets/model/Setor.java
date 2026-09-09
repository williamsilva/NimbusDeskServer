package com.nimbusdesk.tickets.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Setor de TI - Configurações &gt; Setores. Mesmo desenho do {@code com.nimbusflow.tickets.model.
 * Department}: {@code userIds} guarda só os ids do NimbusAuth (sem entidade própria de usuário
 * local). Sufixo "_TI" nas permissões (SETOR_TI_CONSULT/SETOR_TI_MANAGE) é só de nomenclatura no
 * NimbusAuth (ver PROJECT_SPEC.md) - não afeta esta entidade. Sem setor reservado tipo "Gestor"
 * nesta fase.
 */
@Getter
@Setter
@Entity
@Table(name = "setores")
public class Setor {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, length = 120)
  private String nome;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "setor_users", joinColumns = @JoinColumn(name = "setor_id"))
  @Column(name = "user_id", length = 100, nullable = false)
  private Set<String> userIds = new HashSet<>();

  @Column(nullable = false)
  private boolean ativo = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
