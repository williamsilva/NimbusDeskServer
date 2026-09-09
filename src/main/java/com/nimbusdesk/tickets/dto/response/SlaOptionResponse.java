package com.nimbusdesk.tickets.dto.response;

import java.util.UUID;

/** Pro seletor de SLA no formulário de Categoria (Configurações > Categorias) - sem gate de
 *  permissão além de autenticação (mesmo padrão de CategoryOptionResponse/SetorOptionResponse). */
public record SlaOptionResponse(UUID id, String nome) {
}
