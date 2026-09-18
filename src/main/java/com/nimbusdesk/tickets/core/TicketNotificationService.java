package com.nimbusdesk.tickets.core;

import com.nimbusdesk.common.security.NimbusCoreInternalClient;
import com.nimbussystems.commons.notification.mail.EmailSenderService;
import com.nimbussystems.commons.security.NimbusSecurityProperties;
import com.nimbussystems.commons.security.UserDirectoryService;
import com.nimbusdesk.tickets.model.Ticket;
import com.nimbusdesk.tickets.repository.TicketParticipantRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notifica (e-mail) sobre eventos de um Chamado - mesmo espírito de
 * com.nimbusflow.tickets.core.TicketNotificationService (template arquitetural), adaptado pra
 * regra de audiência pedida pelo usuário em 2026-09-08:
 *
 * <ul>
 *   <li>{@link #notifyCreated} - na abertura, avisa o SOLICITANTE (confirmação) e todo mundo com
 *       {@code CHAMADO_MANAGE} (o "T.I." - qualquer técnico/gestor pode assumir, então todos
 *       precisam saber que existe um chamado novo na fila).</li>
 *   <li>{@link #notifyAssigned} - quando alguém do T.I. assume, avisa o SOLICITANTE (seu chamado
 *       foi pego) e o próprio RESPONSÁVEL (confirmação de que assumiu).</li>
 *   <li>{@link #notifyActivity} - a partir daí (novo comentário público, mudança de status), a
 *       audiência ESTREITA: só solicitante + participantes ("compartilhado com") + responsável -
 *       nunca mais todo o T.I. (evita spam pra quem não está envolvido). Quem disparou a ação não
 *       recebe e-mail da própria ação.</li>
 * </ul>
 *
 * Nunca propaga exceção (mesmo critério de TicketNotificationService do NimbusFlow) - falha no
 * envio de e-mail não pode desfazer/bloquear a ação de negócio em si (criar/atribuir/comentar).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketNotificationService {

  private static final String NIMBUSDESK_APP_KEY = "nimbusdesk";
  private static final String CHAMADO_MANAGE_PERMISSION = "CHAMADO_MANAGE";

  private final EmailSenderService emailSenderService;
  private final UserDirectoryService userDirectoryService;
  private final NimbusCoreInternalClient nimbusCoreInternalClient;
  private final TicketParticipantRepository participantRepository;
  private final NimbusSecurityProperties securityProperties;

  public void notifyCreated(Ticket ticket) {
    try {
      Set<String> userIds = new HashSet<>();
      userIds.add(ticket.getSolicitanteId());
      userIds.addAll(resolveTiUserIds());

      Set<String> recipients = resolveEmails(userIds);
      if (recipients.isEmpty()) {
        log.info("Nenhum destinatário resolvido pra abertura do chamado - e-mail não enviado (ticket={})", ticket.getId());
        return;
      }

      send(ticket, recipients, "Novo chamado aberto: " + ticket.getNumero(), "mail/ticket_created", "ticket_created");
    } catch (Exception e) {
      log.error("Falha ao notificar abertura de chamado por e-mail (ticket={})", ticket.getId(), e);
    }
  }

  public void notifyAssigned(Ticket ticket) {
    try {
      Set<String> userIds = new HashSet<>();
      userIds.add(ticket.getSolicitanteId());
      if (ticket.getResponsavelId() != null) {
        userIds.add(ticket.getResponsavelId());
      }

      Set<String> recipients = resolveEmails(userIds);
      if (recipients.isEmpty()) {
        log.info("Nenhum destinatário resolvido pra atribuição do chamado - e-mail não enviado (ticket={})", ticket.getId());
        return;
      }

      send(ticket, recipients, "Chamado atribuído: " + ticket.getNumero(), "mail/ticket_assigned", "ticket_assigned");
    } catch (Exception e) {
      log.error("Falha ao notificar atribuição de chamado por e-mail (ticket={})", ticket.getId(), e);
    }
  }

  /** @param kind rótulo livre pro template ("comment"/"status") - mesmo e-mail genérico de
   *      atividade pros dois casos, só muda a frase de resumo. */
  public void notifyActivity(Ticket ticket, String actorId, String kind) {
    try {
      Set<String> userIds = new HashSet<>();
      userIds.add(ticket.getSolicitanteId());
      if (ticket.getResponsavelId() != null) {
        userIds.add(ticket.getResponsavelId());
      }
      userIds.addAll(participantRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
          .map(p -> p.getUserId())
          .toList());
      userIds.remove(actorId);

      Set<String> recipients = resolveEmails(userIds);
      if (recipients.isEmpty()) {
        log.info("Nenhum destinatário resolvido pra atividade no chamado - e-mail não enviado (ticket={}, kind={})",
            ticket.getId(), kind);
        return;
      }

      String summary = "comment".equals(kind)
          ? "Novo comentário no seu chamado"
          : "O status do seu chamado mudou";

      EmailSenderService.Message message = EmailSenderService.Message.builder()
          .recipients(recipients)
          .subject(summary + ": " + ticket.getNumero())
          .template("mail/ticket_activity")
          .eventType("ticket_activity")
          .data("numero", ticket.getNumero())
          .data("titulo", ticket.getTitulo())
          .data("status", ticket.getStatus().name())
          .data("summary", summary)
          .data("ticketUrl", ticketUrl(ticket))
          .build();

      emailSenderService.sendThymeleaf(message);
    } catch (Exception e) {
      log.error("Falha ao notificar atividade de chamado por e-mail (ticket={}, kind={})", ticket.getId(), kind, e);
    }
  }

  /** Job de alerta "chamados sem responsável" (ver TicketUnassignedAlertJob) - 1 e-mail POR TÉCNICO
   *  de T.I., todos recebendo o MESMO resumo (todo chamado ABERTO sem responsável no momento da
   *  execução) - diferente de {@link #notifyPendingResponseAlert}, que é individualizado por
   *  destinatário; aqui não faz sentido individualizar (qualquer técnico pode assumir qualquer
   *  chamado da fila). */
  public void notifyUnassignedAlert(List<Ticket> tickets) {
    if (tickets.isEmpty()) {
      return;
    }
    try {
      Set<String> recipients = resolveEmails(new HashSet<>(resolveTiUserIds()));
      if (recipients.isEmpty()) {
        log.info("Nenhum técnico de T.I. resolvido - alerta de chamados sem responsável não enviado");
        return;
      }

      EmailSenderService.Message message = EmailSenderService.Message.builder()
          .recipients(recipients)
          .subject("Chamados sem responsável (" + tickets.size() + ")")
          .template("mail/ticket_unassigned_alert")
          .eventType("ticket_unassigned_alert")
          .data("count", tickets.size())
          .data("tickets", ticketRows(tickets))
          .data("listUrl", listUrl())
          .build();

      emailSenderService.sendThymeleaf(message);
    } catch (Exception e) {
      log.error("Falha ao notificar alerta de chamados sem responsável por e-mail", e);
    }
  }

  /** Job de alerta "chamado aguardando SUA resposta" (ver TicketPendingResponseAlertJob) - 1 e-mail
   *  POR DESTINATÁRIO, listando só os chamados que ELE precisa responder (a lista já vem
   *  individualizada por TicketAutomationService#findTicketsPendingResponseByRecipient - solicitante
   *  + participantes quando o lado pendente é o do solicitante, só o responsável quando é o dele). */
  public void notifyPendingResponseAlert(String recipientUserId, List<Ticket> tickets) {
    if (tickets.isEmpty()) {
      return;
    }
    try {
      String email = resolveEmail(recipientUserId);
      if (email == null) {
        log.warn("E-mail não resolvido pro usuário {} - alerta de pendência de resposta não enviado ({} chamado(s))",
            recipientUserId, tickets.size());
        return;
      }

      EmailSenderService.Message message = EmailSenderService.Message.builder()
          .recipients(Set.of(email))
          .subject("Chamado(s) aguardando sua resposta (" + tickets.size() + ")")
          .template("mail/ticket_pending_response_alert")
          .eventType("ticket_pending_response_alert")
          .data("count", tickets.size())
          .data("tickets", ticketRows(tickets))
          .data("listUrl", listUrl())
          .build();

      emailSenderService.sendThymeleaf(message);
    } catch (Exception e) {
      log.error("Falha ao notificar pendência de resposta pro usuário {}", recipientUserId, e);
    }
  }

  /** Linhas pro {@code th:each} dos templates de digest (#notifyUnassignedAlert/
   *  #notifyPendingResponseAlert) - {@code Map} em vez de um record dedicado porque
   *  {@code EmailSenderService.Message#data} é {@code Map<String,Object>} de qualquer forma, e
   *  Thymeleaf/SpringEL acessa chave de Map com a mesma sintaxe de propriedade ({@code ${t.numero}}). */
  private List<Map<String, Object>> ticketRows(List<Ticket> tickets) {
    return tickets.stream()
        .map(t -> Map.<String, Object>of(
            "numero", t.getNumero(),
            "titulo", t.getTitulo(),
            "prioridade", t.getPrioridade().name(),
            "ticketUrl", ticketUrl(t)))
        .toList();
  }

  private void send(Ticket ticket, Set<String> recipients, String subject, String template, String eventType) {
    EmailSenderService.Message message = EmailSenderService.Message.builder()
        .recipients(recipients)
        .subject(subject)
        .template(template)
        .eventType(eventType)
        .data("numero", ticket.getNumero())
        .data("titulo", ticket.getTitulo())
        .data("descricao", ticket.getDescricao())
        .data("prioridade", ticket.getPrioridade().name())
        .data("solicitanteNome", resolveName(ticket.getSolicitanteId()))
        .data("responsavelNome", ticket.getResponsavelId() != null ? resolveName(ticket.getResponsavelId()) : null)
        .data("ticketUrl", ticketUrl(ticket))
        .build();

    emailSenderService.sendThymeleaf(message);
  }

  /** Todo mundo com CHAMADO_MANAGE (app_key=nimbusdesk) - "T.I." não é um grupo fixo no código,
   *  é resolvido ao vivo pela permissão (mesmo padrão de AddendumNotificationService/
   *  PaymentNotificationService no NimbusFlow - evita lista de e-mail manual desincronizada). Não
   *  degrada silenciosamente por completo (loga warn), mas retorna vazio em falha - uma
   *  indisponibilidade do NimbusCore não deve impedir a abertura do chamado em si. */
  private List<String> resolveTiUserIds() {
    try {
      return nimbusCoreInternalClient.fetchOptionsByPermission(NIMBUSDESK_APP_KEY, CHAMADO_MANAGE_PERMISSION).stream()
          .map(u -> u.id().toString())
          .toList();
    } catch (Exception e) {
      log.warn("Falha ao resolver usuários com CHAMADO_MANAGE no NimbusCore: {}", e.getMessage());
      return List.of();
    }
  }

  private Set<String> resolveEmails(Set<String> userIds) {
    Set<String> emails = new HashSet<>();
    for (String userId : userIds) {
      if (userId == null) {
        continue;
      }
      userDirectoryService.summaryFor(UserDirectoryService.parseIdOrNull(userId))
          .map(u -> u.userName())
          .filter(Objects::nonNull)
          .ifPresent(emails::add);
    }
    return emails;
  }

  /** Versão singular de #resolveEmails - usada por #notifyPendingResponseAlert (1 destinatário por
   *  chamada, diferente do digest de #notifyUnassignedAlert). */
  private String resolveEmail(String userId) {
    return resolveEmails(Set.of(userId)).stream().findFirst().orElse(null);
  }

  private String resolveName(String userId) {
    return userDirectoryService.summaryFor(UserDirectoryService.parseIdOrNull(userId)).map(u -> u.name()).orElse(null);
  }

  private String ticketUrl(Ticket ticket) {
    return securityProperties.getWeb().getSpaBaseUrl() + "/tickets/" + ticket.getId();
  }

  private String listUrl() {
    return securityProperties.getWeb().getSpaBaseUrl() + "/tickets";
  }
}
