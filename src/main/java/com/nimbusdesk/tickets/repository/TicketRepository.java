package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.Ticket;
import com.nimbusdesk.tickets.model.TicketStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

  List<Ticket> findAllByOrderByCreatedAtDesc();

  /** Base de "Meus chamados" (ver TicketService#search) - sem gate de CHAMADO_CONSULT, filtrado
   *  server-side por dono (solicitante) ou responsável atribuído. */
  List<Ticket> findBySolicitanteIdOrResponsavelIdOrderByCreatedAtDesc(String solicitanteId, String responsavelId);

  /** Badge do escopo "mine" na lista (ver TicketService#scopeCounts) - mesmo filtro do método
   *  acima, só que contando (em vez de carregar as entidades inteiras) e excluindo os chamados
   *  "encerrados" ({@code terminal} = RESOLVIDO/FECHADO/CANCELADO, ver TicketService#
   *  TERMINAL_STATUSES) - o badge só reflete trabalho ainda em aberto. Precedência OR/AND exige
   *  {@code @Query} explícito aqui: um nome derivado tipo "...OrResponsavelIdAndStatusNotIn"
   *  aplicaria o AND só no segundo termo do OR, não no resultado inteiro. */
  @Query("select count(t) from Ticket t where (t.solicitanteId = :userId or t.responsavelId = :userId) "
      + "and t.status not in :terminal")
  long countActiveMine(@Param("userId") String userId, @Param("terminal") Collection<TicketStatus> terminal);

  /** Base de "Aguardando minha ação" - sem gate de CHAMADO_CONSULT (mesmo racional de "Meus
   *  chamados": só posso estar "aguardando ação" num chamado onde já sou parte, ver
   *  TicketService#applyWaitingAction). */
  List<Ticket> findByAguardandoAcaoDeOrderByCreatedAtDesc(String aguardandoAcaoDe);

  /** Badge do escopo "waiting" na lista (ver TicketService#scopeCounts) - mesmo filtro do método
   *  acima, excluindo chamados "encerrados" (mesmo racional de #countActiveMine - na prática
   *  aguardandoAcaoDe já vira null ao entrar num status terminal, ver TicketService#
   *  applyStatusSideEffects, mas o filtro explícito é mantido por segurança). */
  @Query("select count(t) from Ticket t where t.aguardandoAcaoDe = :userId and t.status not in :terminal")
  long countActiveWaiting(@Param("userId") String userId, @Param("terminal") Collection<TicketStatus> terminal);

  /** Base da "Fila de atendimento" (escopo "queue", exige CHAMADO_MANAGE) - chamados ABERTOS sem
   *  responsável ainda, mais antigos primeiro (ordem de chegada, FIFO - diferente do resto da
   *  busca, que é sempre mais recente primeiro). Ver TicketService#search. */
  List<Ticket> findByStatusAndResponsavelIdIsNullOrderByDataAberturaAsc(TicketStatus status);

  /** Badge do escopo "queue" na lista (ver TicketService#scopeCounts) - mesmo filtro do método
   *  acima, só que contando em vez de carregar as entidades inteiras. Sem filtro de "encerrado"
   *  aqui: {@code status=ABERTO} nunca é terminal, seria sempre redundante. */
  long countByStatusAndResponsavelIdIsNull(TicketStatus status);

  /** Base de "Compartilhados comigo" (ids resolvidos via TicketParticipantRepository#
   *  findTicketIdsByUserId). */
  List<Ticket> findByIdInOrderByCreatedAtDesc(Collection<UUID> ids);

  /** Badge do escopo "shared" na lista (ver TicketService#scopeCounts) - mesmo filtro do método
   *  acima, excluindo chamados "encerrados" (mesmo racional de #countActiveMine). Chamador garante
   *  {@code ids} não-vazio (Hibernate aceita IN vazio, mas não vale a pena rodar a query à toa). */
  @Query("select count(t) from Ticket t where t.id in :ids and t.status not in :terminal")
  long countActiveByIdIn(@Param("ids") Collection<UUID> ids, @Param("terminal") Collection<TicketStatus> terminal);

  /** Badge do escopo "all" na lista (ver TicketService#scopeCounts) - contagem PRÓPRIA (2026-09-08:
   *  não é mais soma de mine+waiting+shared+queue no frontend - um chamado pode satisfazer mais de
   *  um escopo ao mesmo tempo, ex. sou solicitante E estou aguardando ação nele, então a soma
   *  contava ele 2x; "all" é a base real do escopo "all" em #search, sem filtro de dono/participante
   *  nenhum, só excluindo "encerrado"). */
  long countByStatusNotIn(Collection<TicketStatus> terminal);

  /** Sequence dedicada (ver V...__tickets.sql) - {@code nextval} é atômico no Postgres, não precisa
   *  de lock explícito nem de retry em caso de concorrência (ver TicketService#nextNumero). */
  @Query(value = "select nextval('ticket_numero_seq')", nativeQuery = true)
  long nextNumeroSequenceValue();

  /** Base do job de alerta de pendência de resposta (ver TicketAutomationService#
   *  findTicketsPendingResponseByRecipient) - qualquer chamado ainda ativo aguardando ação de
   *  alguém (solicitante OU responsável, nunca terminal). */
  List<Ticket> findByAguardandoAcaoDeIsNotNullAndStatusNotIn(Collection<TicketStatus> terminal);

  /** Base do job de auto-fechamento (ver TicketAutomationService#findTicketsEligibleForAutoClose/
   *  TicketService#autoCloseResolved) - RESOLVIDO há pelo menos "carência" dias. Só ids, não a
   *  entidade inteira - o job relê cada um por id na hora de fechar, evita carregar tudo em memória
   *  à toa se a lista for grande. */
  @Query("select t.id from Ticket t where t.status = :status and t.dataResolucao < :cutoff")
  List<UUID> findIdsByStatusAndDataResolucaoBefore(@Param("status") TicketStatus status, @Param("cutoff") Instant cutoff);
}
