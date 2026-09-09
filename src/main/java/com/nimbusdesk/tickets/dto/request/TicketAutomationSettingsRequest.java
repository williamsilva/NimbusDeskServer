package com.nimbusdesk.tickets.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TicketAutomationSettingsRequest(
    @NotNull @Min(1) @Max(365) Integer unassignedAlertPeriodicidadeDias,
    @NotNull @Min(1) @Max(365) Integer pendingResponseAlertPeriodicidadeDias,
    @NotNull @Min(1) @Max(365) Integer autoClosePeriodicidadeDias,
    @NotNull @Min(1) @Max(365) Integer autoCloseCarenciaDias) {
}
