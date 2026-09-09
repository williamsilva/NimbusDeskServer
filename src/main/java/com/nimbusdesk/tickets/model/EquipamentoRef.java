package com.nimbusdesk.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Cópia local, somente leitura pelo app - escrita só pelo job de sincronização (ver
 * com.nimbusdesk.tickets.core.EquipamentoSyncService). {@code id} é o MESMO UUID do Equipamento no
 * NimbusFlowServer (chave de correlação, {@code @Id} sem {@code @GeneratedValue} - atribuído pelo
 * job, nunca gerado localmente), por isso não há {@code @CreationTimestamp} clássico aqui:
 * {@code sincronizadoEm} já cumpre esse papel (timestamp do último upsert local).
 */
@Getter
@Setter
@Entity
@Table(name = "equipamento_ref")
public class EquipamentoRef {

  @Id
  private UUID id;

  @Column(nullable = false, length = 60)
  private String codigo;

  @Column(nullable = false, length = 200)
  private String nome;

  @Column(length = 200)
  private String localizacao;

  @Column(name = "status_operacional", length = 40)
  private String statusOperacional;

  /** {@code updatedAt} de origem (NimbusFlowServer) - usado só como metadado de exibição, não
   *  como cursor da sincronização (ver EquipamentoSyncService pro porquê). */
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  @Column(name = "sincronizado_em", nullable = false)
  private Instant sincronizadoEm;
}
