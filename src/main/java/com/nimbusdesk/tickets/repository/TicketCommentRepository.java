package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.TicketComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

  List<TicketComment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
