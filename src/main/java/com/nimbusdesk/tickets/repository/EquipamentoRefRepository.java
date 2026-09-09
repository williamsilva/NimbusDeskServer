package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.EquipamentoRef;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipamentoRefRepository extends JpaRepository<EquipamentoRef, UUID> {
}
