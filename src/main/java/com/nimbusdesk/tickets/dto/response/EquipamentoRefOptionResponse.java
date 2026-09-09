package com.nimbusdesk.tickets.dto.response;

import java.util.UUID;

/** Pro seletor de equipamento no formulário de abertura de chamado - sem gate de permissão. */
public record EquipamentoRefOptionResponse(UUID id, String codigo, String nome) {
}
