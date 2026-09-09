package com.nimbusdesk.tickets.dto.response;

import java.time.Instant;

public record TicketAutomationSettingsResponse(
    int unassignedAlertPeriodicidadeDias,
    int pendingResponseAlertPeriodicidadeDias,
    int autoClosePeriodicidadeDias,
    int autoCloseCarenciaDias,
    Instant updatedAt) {
}
