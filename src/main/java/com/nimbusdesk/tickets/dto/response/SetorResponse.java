package com.nimbusdesk.tickets.dto.response;

import com.nimbussystems.commons.security.UserMinimalResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SetorResponse(
    UUID id,
    String nome,
    Set<String> userIds,
    /** Resolvido ao vivo via UserDirectoryService, mesmo padrão de DepartmentResponse no
     *  NimbusFlow - não é uma coluna da entidade. */
    List<UserMinimalResponse> users,
    boolean ativo,
    Instant createdAt,
    Instant updatedAt) {
}
