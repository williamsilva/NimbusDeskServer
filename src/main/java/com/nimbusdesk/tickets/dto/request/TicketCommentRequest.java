package com.nimbusdesk.tickets.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketCommentRequest(@NotBlank @Size(max = 4000) String mensagem, boolean interno) {
}
