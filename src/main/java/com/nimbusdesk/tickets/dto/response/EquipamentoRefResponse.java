package com.nimbusdesk.tickets.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EquipamentoRefResponse(
    UUID id,
    String codigo,
    String nome,
    String localizacao,
    String statusOperacional,
    Instant atualizadoEm,
    Instant sincronizadoEm) {
}
