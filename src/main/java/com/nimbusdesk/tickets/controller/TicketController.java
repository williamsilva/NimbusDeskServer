package com.nimbusdesk.tickets.controller;

import com.nimbussystems.commons.security.bff.admin.AdminUserMinimalResponse;
import com.nimbussystems.commons.web.PageResponse;
import com.nimbussystems.commons.web.SearchRequest;
import com.nimbusdesk.tickets.core.TicketAttachmentService;
import com.nimbusdesk.tickets.core.TicketCommentService;
import com.nimbusdesk.tickets.core.TicketEventBroadcaster;
import com.nimbusdesk.tickets.core.TicketService;
import com.nimbusdesk.tickets.dto.request.TicketAssignRequest;
import com.nimbusdesk.tickets.dto.request.TicketCommentRequest;
import com.nimbusdesk.tickets.dto.request.TicketParticipantsRequest;
import com.nimbusdesk.tickets.dto.request.TicketRequest;
import com.nimbusdesk.tickets.dto.request.TicketStatusRequest;
import com.nimbusdesk.tickets.dto.response.TicketAttachmentResponse;
import com.nimbusdesk.tickets.dto.response.TicketAttachmentUrlResponse;
import com.nimbusdesk.tickets.dto.response.TicketCommentResponse;
import com.nimbusdesk.tickets.dto.response.TicketParticipantResponse;
import com.nimbusdesk.tickets.dto.response.TicketResponse;
import com.nimbusdesk.tickets.dto.response.TicketScopeCountsResponse;
import com.nimbusdesk.tickets.dto.response.TicketStatusHistoryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chamado de TI (helpdesk) - ver PROJECT_SPEC.md pro contrato completo. Todo gate de permissão
 * (CHAMADO_CONSULT/CHAMADO_MANAGE, visibilidade "meu chamado") é checado IMPERATIVAMENTE dentro
 * dos Services (TicketService/TicketCommentService/TicketAttachmentService via NdSecurity) porque
 * depende de dado carregado em runtime (dono/responsável, flag "mine" do corpo da busca) - não há
 * {@code @CheckSecurity} nesta classe de propósito (ver Javadoc de CheckSecurity.Ticket).
 */
@RestController
@RequestMapping("/bff/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

  private final TicketService ticketService;
  private final TicketCommentService ticketCommentService;
  private final TicketAttachmentService ticketAttachmentService;
  private final TicketEventBroadcaster ticketEventBroadcaster;

  @PostMapping("/search")
  public PageResponse<TicketResponse> search(@RequestBody SearchRequest request) {
    return ticketService.search(request);
  }

  /** Server-Sent Events - "algo mudou num chamado" (criação/modificação), pra lista/detalhe se
   *  atualizarem sozinhos sem precisar de "Atualizar" manual (ver TicketEventBroadcaster,
   *  TicketEventsService no frontend). Conexão longa (GET comum, sem WebSocket/broker) - qualquer
   *  usuário autenticado pode assinar, o payload não carrega nada sensível (ver
   *  TicketEventBroadcaster#broadcast). */
  @GetMapping("/events")
  public SseEmitter events() {
    return ticketEventBroadcaster.subscribe();
  }

  /** Badges dos escopos "Aguardando minha ação"/"Fila de atendimento" na p-selectButton do
   *  frontend (ver TicketsListComponent#scopeOptions). */
  @GetMapping("/scope-counts")
  public TicketScopeCountsResponse scopeCounts() {
    return ticketService.scopeCounts();
  }

  @GetMapping("/{id}")
  public TicketResponse getById(@PathVariable UUID id) {
    return ticketService.getById(id);
  }

  /** Só usuários com CHAMADO_MANAGE (T.I.) - seletor "Atribuir responsável" da tela de detalhe
   *  (ver TicketDetailComponent, TicketService#responsavelOptions). Diferente de
   *  {@code GET /bff/v1/users/options} (todo usuário do NimbusDesk, inclusive SOLICITANTE - usado
   *  em "Compartilhado com"). */
  @GetMapping("/responsavel-options")
  public List<AdminUserMinimalResponse> responsavelOptions() {
    return ticketService.responsavelOptions();
  }

  @PostMapping(consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  public TicketResponse create(
      @Valid @RequestPart("data") TicketRequest data,
      @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
    return ticketService.create(data, attachments);
  }

  @PutMapping("/{id}")
  public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody TicketRequest request) {
    return ticketService.update(id, request);
  }

  @PutMapping("/{id}/assign")
  public TicketResponse assign(@PathVariable UUID id, @Valid @RequestBody TicketAssignRequest request) {
    return ticketService.assign(id, request);
  }

  @PutMapping("/{id}/status")
  public TicketResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody TicketStatusRequest request) {
    return ticketService.changeStatus(id, request);
  }

  /** multipart (não mais JSON puro, desde 2026-09-08): "data" + "attachments" opcional - anexo
   *  agora só existe vinculado a um comentário (ver TicketAttachmentService, sem endpoint solto de
   *  upload). */
  @PostMapping(value = "/{id}/comments", consumes = "multipart/form-data")
  @ResponseStatus(HttpStatus.CREATED)
  public TicketCommentResponse addComment(
      @PathVariable UUID id,
      @Valid @RequestPart("data") TicketCommentRequest request,
      @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
    return ticketCommentService.add(id, request, attachments);
  }

  @GetMapping("/{id}/comments")
  public List<TicketCommentResponse> listComments(@PathVariable UUID id) {
    return ticketCommentService.list(id);
  }

  @GetMapping("/{id}/history")
  public List<TicketStatusHistoryResponse> listHistory(@PathVariable UUID id) {
    return ticketService.listHistory(id);
  }

  @GetMapping("/{id}/attachments/{attachmentId}/url")
  public TicketAttachmentUrlResponse attachmentUrl(@PathVariable UUID id, @PathVariable UUID attachmentId) {
    return new TicketAttachmentUrlResponse(ticketAttachmentService.presignedUrl(id, attachmentId));
  }

  @GetMapping("/{id}/participants")
  public List<TicketParticipantResponse> listParticipants(@PathVariable UUID id) {
    return ticketService.listParticipants(id);
  }

  @PutMapping("/{id}/participants")
  public List<TicketParticipantResponse> replaceParticipants(
      @PathVariable UUID id, @RequestBody TicketParticipantsRequest request) {
    return ticketService.replaceParticipants(id, request);
  }
}
