package com.nimbusdesk.tickets.core;

import com.nimbusdesk.common.security.NdSecurity;
import com.nimbusdesk.common.storage.FileValidator;
import com.nimbusdesk.common.storage.StorageService;
import com.nimbussystems.commons.security.CurrentUserProvider;
import com.nimbusdesk.tickets.dto.response.TicketAttachmentResponse;
import com.nimbusdesk.tickets.model.Ticket;
import com.nimbusdesk.tickets.model.TicketAttachment;
import com.nimbusdesk.tickets.model.TicketComment;
import com.nimbusdesk.tickets.repository.TicketAttachmentRepository;
import com.nimbusdesk.tickets.repository.TicketParticipantRepository;
import com.nimbusdesk.tickets.repository.TicketRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Anexo do chamado - upload grava a key (MinIO), leitura gera URL pré-assinada sob demanda (ver
 * com.nimbusdesk.common.storage.StorageService). TODO anexo está sempre vinculado a um comentário
 * (ver {@link #saveAllForComment}, usado por TicketCommentService#add e #createOpeningComment) -
 * decisão de 2026-09-08: nem os anexos da ABERTURA do chamado escapam disso mais (a descrição
 * virou o primeiro comentário da conversa, ver TicketCommentService#createOpeningComment) - não
 * existe mais upload "solto" sem comentário nenhum, pra melhorar a visualização (anexo sempre
 * aparece junto da mensagem que o trouxe).
 */
@Service
@RequiredArgsConstructor
public class TicketAttachmentService {

  private final TicketRepository ticketRepository;
  private final TicketAttachmentRepository attachmentRepository;
  private final TicketParticipantRepository participantRepository;
  private final StorageService storageService;
  private final FileValidator fileValidator;
  private final CurrentUserProvider currentUserProvider;
  private final NdSecurity ndSecurity;

  /** Anexos enviados junto de um comentário - tanto os de resposta (ver TicketCommentService#add)
   *  quanto os da própria abertura do chamado (ver #createOpeningComment). Checagem de
   *  permissão/visibilidade já foi feita pelo chamador antes de criar o comentário em si, não
   *  repete aqui. */
  @Transactional
  List<TicketAttachment> saveAllForComment(Ticket ticket, TicketComment comment, List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      return List.of();
    }
    return files.stream()
        .filter(f -> f != null && !f.isEmpty())
        .map(f -> saveOne(ticket, comment, f))
        .toList();
  }

  /** Reaproveitado por TicketCommentService pra montar TicketCommentResponse#attachments. */
  List<TicketAttachmentResponse> toResponses(List<TicketAttachment> attachments) {
    return attachments.stream().map(this::toResponse).toList();
  }

  /** {@code GET /bff/v1/tickets/{id}/attachments/{attachmentId}/url} - TTL curto (ver
   *  StorageProperties#presignedUrlTtl), nunca cacheada/persistida. Mesma regra de visibilidade do
   *  chamado (não documentada explicitamente no contrato do endpoint, mas necessária: sem isto,
   *  qualquer usuário autenticado que soubesse o attachmentId conseguiria ler o anexo de um chamado
   *  que não pode ver). */
  @Transactional(readOnly = true)
  public String presignedUrl(UUID ticketId, UUID attachmentId) {
    Ticket ticket = getOrThrow(ticketId);
    requireView(ticket);

    TicketAttachment attachment = attachmentRepository.findById(attachmentId)
        .filter(a -> a.getTicket().getId().equals(ticketId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found: " + attachmentId));

    return storageService.presignedGetUrl(attachment.getStorageKey()).toString();
  }

  private TicketAttachment saveOne(Ticket ticket, TicketComment comment, MultipartFile file) {
    fileValidator.validate(file);
    String key = "tickets/%s/%s-%s".formatted(ticket.getId(), UUID.randomUUID(), file.getOriginalFilename());
    storageService.upload(key, file);

    TicketAttachment attachment = new TicketAttachment();
    attachment.setTicket(ticket);
    attachment.setComment(comment);
    attachment.setNomeArquivo(file.getOriginalFilename());
    attachment.setStorageKey(key);
    attachment.setTipoMime(file.getContentType());
    attachment.setTamanhoBytes(file.getSize());
    return attachmentRepository.save(attachment);
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

  private TicketAttachmentResponse toResponse(TicketAttachment attachment) {
    return new TicketAttachmentResponse(
        attachment.getId(), attachment.getTicket().getId(), attachment.getNomeArquivo(),
        attachment.getTipoMime(), attachment.getTamanhoBytes(), attachment.getCreatedAt());
  }
}
