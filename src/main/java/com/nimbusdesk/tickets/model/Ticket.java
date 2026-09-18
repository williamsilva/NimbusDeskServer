package com.nimbusdesk.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Chamado de TI (helpdesk) - ver PROJECT_SPEC.md ("Modelo de domínio &gt; Ticket") pro contrato
 * completo. {@code categoriaId}/{@code slaId}/{@code setorId}/{@code equipamentoRefId} são colunas
 * cruas com FK de banco (sem {@code @ManyToOne}) - mesma técnica de referência entre agregados já
 * usada em todo o NimbusFlow (ver com.nimbusflow.tickets.model.Ticket#workId), aqui aplicada tanto
 * intra-módulo (categoria/sla/setor, todos em com.nimbusdesk.tickets) quanto entre módulos-raiz
 * (equipamentoRefId, resolvido só localmente via {@link com.nimbusdesk.tickets.repository.
 * EquipamentoRefRepository}, nunca contra o NimbusFlowServer ao vivo).
 */
@Getter
@Setter
@Entity
@Table(name = "tickets")
public class Ticket {

  @Id
  @GeneratedValue
  private UUID id;

  /** Sequencial formatado ("CH-2026-00001"), gerado via sequence do Postgres na criação - ver
   *  TicketService#nextNumero. Único (índice UNIQUE na migration). */
  @Column(nullable = false, length = 20)
  private String numero;

  @Column(nullable = false, length = 200)
  private String titulo;

  @Column(nullable = false, length = 2000)
  private String descricao;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TicketStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TicketPriority prioridade;

  @Column(name = "categoria_id", nullable = false)
  private UUID categoriaId;

  /** Derivado de {@code categoria.slaId} na abertura (e recalculado se a categoria mudar via
   *  update, ver TicketService#update) - nunca setado manualmente pelo usuário. */
  @Column(name = "sla_id", nullable = false)
  private UUID slaId;

  @Column(name = "setor_id")
  private UUID setorId;

  @Column(name = "solicitante_id", nullable = false, length = 100)
  private String solicitanteId;

  @Column(name = "responsavel_id", length = 100)
  private String responsavelId;

  /** "De quem é a vez de agir" - raw id do NimbusCore (solicitante OU responsável, nunca um
   *  participante). Atualizado em toda transição de status relevante (assign/changeStatus) e todo
   *  comentário PÚBLICO trocado entre solicitante/responsável - ver TicketService/
   *  TicketCommentService. {@code null} = ninguém pendente (chamado ainda não atribuído, ou
   *  encerrado). Alimenta o escopo "Aguardando minha ação" (ver TicketResponse#aguardandoMinhaAcao). */
  @Column(name = "aguardando_acao_de", length = 100)
  private String aguardandoAcaoDe;

  @Column(name = "equipamento_ref_id")
  private UUID equipamentoRefId;

  @Enumerated(EnumType.STRING)
  @Column(name = "canal_origem", nullable = false, length = 20)
  private TicketChannel canalOrigem;

  @Column(name = "data_abertura", nullable = false)
  private Instant dataAbertura;

  /** Calculada na abertura: {@code dataAbertura + sla.tempoResolucaoMin} (minutos) do SLA da
   *  categoria escolhida. "Estourado" (dataLimiteSla &lt; now &amp;&amp; status ainda ativo) é
   *  calculado em leitura (ver TicketResponse#slaEstourado), nunca persistido. */
  @Column(name = "data_limite_sla", nullable = false)
  private Instant dataLimiteSla;

  @Column(name = "data_resolucao")
  private Instant dataResolucao;

  @Column(name = "data_fechamento")
  private Instant dataFechamento;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
