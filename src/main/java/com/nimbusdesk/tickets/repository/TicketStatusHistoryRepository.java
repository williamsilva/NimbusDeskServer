package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.TicketStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStatusHistoryRepository extends JpaRepository<TicketStatusHistory, UUID> {

  List<TicketStatusHistory> findByTicketIdOrderByChangedAtAsc(UUID ticketId);
}
