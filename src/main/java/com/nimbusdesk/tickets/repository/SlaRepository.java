package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.Sla;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaRepository extends JpaRepository<Sla, UUID> {
}
