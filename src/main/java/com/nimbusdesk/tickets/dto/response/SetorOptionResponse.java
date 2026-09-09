package com.nimbusdesk.tickets.dto.response;

import java.util.UUID;

/** Pro seletor de setor no formulário de abertura de chamado - sem gate de permissão. */
public record SetorOptionResponse(UUID id, String nome) {
}
