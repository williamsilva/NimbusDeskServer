package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.TicketParticipant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketParticipantRepository extends JpaRepository<TicketParticipant, UUID> {

  List<TicketParticipant> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

  /** Batch pra toResponseList (mesmo racional de attachments - evita N+1). */
  List<TicketParticipant> findByTicketIdIn(Collection<UUID> ticketIds);

  boolean existsByTicketIdAndUserId(UUID ticketId, String userId);

  void deleteByTicketId(UUID ticketId);

  /** Base do escopo "Compartilhados comigo" (ver TicketService#search) - ids dos chamados onde o
   *  usuário é participante (nunca solicitante/responsável, ver TicketRepository#
   *  findBySolicitanteIdOrResponsavelIdOrderByCreatedAtDesc pro "Meus chamados"). */
  @Query("select p.ticket.id from TicketParticipant p where p.userId = :userId")
  List<UUID> findTicketIdsByUserId(@Param("userId") String userId);
}
