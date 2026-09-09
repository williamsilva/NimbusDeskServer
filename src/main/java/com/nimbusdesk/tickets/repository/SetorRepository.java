package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.Setor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetorRepository extends JpaRepository<Setor, UUID> {
}
