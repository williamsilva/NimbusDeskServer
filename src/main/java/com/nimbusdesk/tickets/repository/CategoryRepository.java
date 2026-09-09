package com.nimbusdesk.tickets.repository;

import com.nimbusdesk.tickets.model.Category;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
