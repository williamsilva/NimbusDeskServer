package com.nimbusdesk.tickets.core;

import com.nimbusdesk.common.security.NdSecurity;
import com.nimbussystems.commons.security.CurrentUserProvider;
import com.nimbussystems.commons.security.UserDirectoryService;
import com.nimbusdesk.tickets.dto.request.TicketCommentRequest;
import com.nimbusdesk.tickets.dto.response.TicketAttachmentResponse;
import com.nimbusdesk.tickets.dto.response.TicketCommentResponse;
import com.nimbusdesk.tickets.model.Ticket;
import com.nimbusdesk.tickets.model.TicketAttachment;
import com.nimbusdesk.tickets.model.TicketComment;
import com.nimbusdesk.tickets.model.TicketStatus;
import com.nimbusdesk.tickets.model.TicketStatusHistory;
import com.nimbusdesk.tickets.repository.TicketAttachmentRepository;
import com.nimbusdesk.tickets.repository.TicketCommentRepository;
import com.nimbusdesk.tickets.repository.TicketParticipantRepository;
import com.nimbusdesk.tickets.repository.TicketRepository;
import com.nimbusdesk.tickets.repository.TicketStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Comentário do chamado - {@code interno=true} exige CHAMADO_MANAGE (comentário de staff, não
 * visível pro solicitante); {@code interno=false} exige só visibilidade do chamado (mesma regra do
 * GET by id) - ver PROJECT_SPEC.md.
 */
@Service
@RequiredArgsConstructor
public class TicketCommentService {

  /** Status em que o chamado já está com um responsável trabalhando ativamente - só nesses a troca
   *  de comentário público dispara a troca automática de status (ver #applyWaitingAction). */
  private static final Set<TicketStatus> ACTIVE_ASSIGNED_STATUSES =
      Set.of(TicketStatus.EM_ANDAMENTO, TicketStatus.AGUARDANDO_SOLICITANTE, TicketStatus.AGUARDANDO_RESPOSTA);

  private final TicketRepository ticketRepository;
  private final TicketCommentRepository commentRepository;
  private final TicketAttachmentRepository attachmentRepository;
  private final TicketParticipantRepository participantRepository;
  private final TicketStatusHistoryRepository statusHistoryRepository;
  private final TicketAttachmentService ticketAttachmentService;
  private final TicketEventBroadcaster ticketEventBroadcaster;
  private final TicketNotificationService ticketNotificationService;
  private final CurrentUserProvider currentUserProvider;
  private final UserDirectoryService userDirectoryService;
  private final NdSecurity ndSecurity;

  /** {@code attachments} (2026-09-08): anexo só existe vinculado a um comentário (ver
   *  TicketAttachmentService, sem upload solto) - opcional em qualquer comentário, público ou
   *  interno.
   *
   * <p>2026-09-08: chamado "encerrado" (RESOLVIDO/FECHADO/CANCELADO, ver TicketStatusTransitions#
   * TERMINAL_STATUSES) não aceita mais NENHUMA interação - nem público, nem interno (decisão do
   * usuário). Anexo é sempre vinculado a um comentário, então bloquear aqui já bloqueia anexo
   * novo também, sem precisar de checagem própria em TicketAttachmentService. */
  @Transactional
  public TicketCommentResponse add(UUID ticketId, TicketCommentRequest request, List<MultipartFile> attachments) {
    Ticket ticket = getOrThrow(ticketId);

    if (TicketStatusTransitions.isTerminal(ticket.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Ticket status does not accept comments: " + ticket.getStatus());
    }

    if (request.interno()) {
      if (!ndSecurity.canManageChamados()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing CHAMADO_MANAGE authority for an internal comment");
      }
    } else {
      requireView(ticket);
    }

    String autorId = currentUserProvider.requireUserId();

    TicketComment comment = new TicketComment();
    comment.setTicket(ticket);
    comment.setAutorId(autorId);
    comment.setMensagem(request.mensagem());
    comment.setInterno(request.interno());
    comment = commentRepository.save(comment);

    List<TicketAttachment> saved = ticketAttachmentService.saveAllForComment(ticket, comment, attachments);

    applyWaitingAction(ticket, autorId, request.interno());

    // Só comentário PÚBLICO notifica (solicitante/participantes/responsável) - comentário interno
    // é staff-only, o solicitante nem consegue ver (ver #list), não faz sentido notificá-lo.
    if (!request.interno()) {
      ticketNotificationService.notifyActivity(ticket, autorId, "comment");
    }

    // Comentário (mesmo interno) pode ter mudado status/aguardandoAcaoDe (ver #applyWaitingAction)
    // - avisa quem estiver com a tela do chamado aberta, pra atualizar sozinha (ver
    // TicketEventBroadcaster/TicketEventsService no frontend).
    ticketEventBroadcaster.broadcastUpdated(ticket);

    return toResponse(comment, ticketAttachmentService.toResponses(saved));
  }

  /**
   * Comentário sintético criado junto da ABERTURA do chamado (2026-09-08): a descrição informada
   * na abertura passa a existir como o PRIMEIRO comentário da conversa (autor=solicitante,
   * público), em vez de um bloco de texto solto separado da timeline - ver ticket-detail no
   * frontend, que parou de renderizar {@code ticket.descricao} à parte. Isso também fecha de vez a
   * exceção que ainda existia pros "anexos da abertura" (sem comentário associado) - com este
   * método, TODO anexo do chamado sempre está vinculado a um comentário, sem exceção nenhuma (ver
   * TicketAttachmentService, que não tem mais {@code saveAllNoCheck}).
   *
   * <p>Chamado por {@code TicketService#create}, na mesma transação - sem checagem de permissão
   * própria (quem está criando o chamado já é, por definição, o autor deste primeiro comentário) e
   * sem notificação própria ({@code TicketNotificationService#notifyCreated}, chamado logo depois
   * por TicketService, já cobre o e-mail de abertura - que inclui a descrição - evitando e-mail
   * duplicado).
   */
  @Transactional
  void createOpeningComment(Ticket ticket, String descricao, List<MultipartFile> attachments) {
    TicketComment comment = new TicketComment();
    comment.setTicket(ticket);
    comment.setAutorId(ticket.getSolicitanteId());
    comment.setMensagem(descricao);
    comment.setInterno(false);
    comment = commentRepository.save(comment);

    ticketAttachmentService.saveAllForComment(ticket, comment, attachments);
  }

  /**
   * Comentário PÚBLICO trocado entre "lado do solicitante" e responsável muda "de quem é a vez de
   * agir" (ver Ticket#aguardandoAcaoDe) - decisão de design (não detalhada no PROJECT_SPEC.md):
   * comentário INTERNO (staff-only, o solicitante nem vê) não mexe nisso.
   *
   * <p>2026-09-09: participante ("compartilhado com" - ver TicketParticipant) conta como "lado do
   * solicitante" pra este cálculo (decisão do usuário) - mesmo agrupamento visual já usado no chat
   * do frontend (ver TicketDetailComponent#commentGroup, onde solicitante/participante ficam do
   * mesmo lado da bolha). Comentário de um participante também indica "T.I. precisa responder",
   * exatamente como se fosse o próprio solicitante comentando.
   *
   * <p>2026-09-08: além de {@code aguardandoAcaoDe}, quando o chamado já está atribuído e em algum
   * dos status "ativos" ({@link #ACTIVE_ASSIGNED_STATUSES}), o próprio status muda automaticamente
   * pra refletir de quem é a vez - {@code AGUARDANDO_RESPOSTA} se quem comentou foi o solicitante
   * ou um participante (T.I. precisa responder), {@code AGUARDANDO_SOLICITANTE} se foi o
   * responsável. Não dispara e-mail extra de "status" - o e-mail de "comment" já enviado em #add
   * cobre essa notificação.
   */
  private void applyWaitingAction(Ticket ticket, String autorId, boolean interno) {
    if (interno) {
      return;
    }

    boolean isSolicitante = autorId.equals(ticket.getSolicitanteId());
    boolean isResponsavel = ticket.getResponsavelId() != null && autorId.equals(ticket.getResponsavelId());
    boolean isParticipante = !isSolicitante && !isResponsavel
        && participantRepository.existsByTicketIdAndUserId(ticket.getId(), autorId);

    if (!isSolicitante && !isResponsavel && !isParticipante) {
      return; // nem solicitante, nem responsável, nem participante - não mexe em nada
    }

    boolean isLadoSolicitante = isSolicitante || isParticipante;

    ticket.setAguardandoAcaoDe(isLadoSolicitante ? ticket.getResponsavelId() : ticket.getSolicitanteId());

    if (ticket.getResponsavelId() != null && ACTIVE_ASSIGNED_STATUSES.contains(ticket.getStatus())) {
      TicketStatus previous = ticket.getStatus();
      TicketStatus next = isLadoSolicitante ? TicketStatus.AGUARDANDO_RESPOSTA : TicketStatus.AGUARDANDO_SOLICITANTE;
      if (previous != next) {
        ticket.setStatus(next);
        ticketRepository.save(ticket);
        writeHistory(ticket, previous, next, autorId);
        return;
      }
    }

    ticketRepository.save(ticket);
  }

  private void writeHistory(Ticket ticket, TicketStatus previous, TicketStatus next, String autorId) {
    TicketStatusHistory history = new TicketStatusHistory();
    history.setTicket(ticket);
    history.setStatusAnterior(previous);
    history.setStatusNovo(next);
    history.setUsuarioId(autorId);
    history.setChangedAt(Instant.now());
    statusHistoryRepository.save(history);
  }

  /** Filtra comentários internos se o chamador não tiver CHAMADO_MANAGE (ver PROJECT_SPEC.md). */
  @Transactional(readOnly = true)
  public List<TicketCommentResponse> list(UUID ticketId) {
    Ticket ticket = getOrThrow(ticketId);
    requireView(ticket);
    boolean canSeeInternal = ndSecurity.canManageChamados();

    List<TicketComment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
        .filter(c -> canSeeInternal || !c.isInterno())
        .toList();

    List<UUID> commentIds = comments.stream().map(TicketComment::getId).toList();
    Map<UUID, List<TicketAttachment>> attachmentsByCommentId = commentIds.isEmpty()
        ? Map.of()
        : attachmentRepository.findByCommentIdIn(commentIds).stream()
            .collect(Collectors.groupingBy(a -> a.getComment().getId()));

    return comments.stream()
        .map(c -> toResponse(c, ticketAttachmentService.toResponses(
            attachmentsByCommentId.getOrDefault(c.getId(), List.of()))))
        .toList();
  }

  private void requireView(Ticket ticket) {
    String userId = currentUserProvider.getCurrentUser().userId();
    boolean isParticipant = userId != null && participantRepository.existsByTicketIdAndUserId(ticket.getId(), userId);
    boolean visible = TicketVisibility.isMine(ticket, userId, isParticipant) || ndSecurity.canConsultChamados();
    if (!visible) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ticket not visible to current user");
    }
  }

  private Ticket getOrThrow(UUID id) {
    return ticketRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + id));
  }

  private TicketCommentResponse toResponse(TicketComment comment, List<TicketAttachmentResponse> attachments) {
    return new TicketCommentResponse(
        comment.getId(), comment.getTicket().getId(), comment.getAutorId(),
        resolveUserName(comment.getAutorId()), comment.getMensagem(), comment.isInterno(),
        attachments, comment.getCreatedAt());
  }

  private String resolveUserName(String userId) {
    return userDirectoryService.summaryFor(UserDirectoryService.parseIdOrNull(userId)).map(u -> u.name()).orElse(null);
  }
}
