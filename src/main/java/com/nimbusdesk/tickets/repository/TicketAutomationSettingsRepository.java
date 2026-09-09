package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.TicketAutomationSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAutomationSettingsRepository extends JpaRepository<TicketAutomationSettings, UUID> {

  Optional<TicketAutomationSettings> findFirstBy();
}
