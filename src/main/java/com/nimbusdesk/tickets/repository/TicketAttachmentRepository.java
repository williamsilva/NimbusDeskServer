package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.TicketAttachment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

  /** Resolução em lote pro TicketCommentService#list - um anexo por comentário, agrupados pelo
   *  chamador. TODO anexo está vinculado a um comentário (o de abertura incluso, ver
   *  TicketCommentService#createOpeningComment), não existe mais busca por "anexo sem comentário". */
  List<TicketAttachment> findByCommentIdIn(Collection<UUID> commentIds);
}
