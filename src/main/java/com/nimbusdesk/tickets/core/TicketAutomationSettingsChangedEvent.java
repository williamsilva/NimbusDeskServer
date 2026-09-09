package com.nimbusdesk.tickets.core;

/** Publicado por {@link TicketAutomationSettingsService#update} - os 3 schedulers de automação de
 *  chamados (TicketUnassignedAlertScheduler/TicketPendingResponseAlertScheduler/
 *  TicketAutoCloseScheduler) escutam pra reagendar em runtime, sem precisar de redeploy (mesmo
 *  padrão de com.nimbusflow.works.core.WorkAutoCompleteSettingsChangedEvent no NimbusFlowServer). */
public record TicketAutomationSettingsChangedEvent() {
}
