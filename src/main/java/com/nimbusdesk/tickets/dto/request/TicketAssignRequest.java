package com.nimbusdesk.tickets.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TicketAssignRequest(@NotBlank String responsavelId) {
}
