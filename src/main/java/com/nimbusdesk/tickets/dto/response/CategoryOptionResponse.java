package com.nimbusdesk.tickets.dto.response;

import java.util.UUID;

/** Pro seletor de categoria no formulário de abertura de chamado - sem gate de permissão. */
public record CategoryOptionResponse(UUID id, String nome) {
}
