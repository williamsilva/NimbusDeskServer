package com.nimbusdesk.common.notification.mail;

import com.nimbussystems.commons.notification.mail.EmailLogEntity;

import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Extends {@code RepresentationModelAssembler} puro, não {@code RepresentationModelAssemblerSupport}
 *  - este último monta um link {@code self} via reflexão num endpoint de detalhe
 *  ({@code @GetMapping("/{id}")}) que não existe aqui (ver {@link BffEmailLogController} - só
 *  leitura em lote, sem endpoint por id). */
@Component
public class EmailLogModelAssembler implements RepresentationModelAssembler<EmailLogEntity, EmailLogModel> {

  @Override
  public EmailLogModel toModel(EmailLogEntity entity) {
    EmailLogModel model = new EmailLogModel();

    model.setId(entity.getId());
    model.setEventType(entity.getEventType());
    model.setRecipients(entity.getRecipients());
    model.setSubject(entity.getSubject());
    model.setTemplate(entity.getTemplate());
    model.setBody(entity.getBody());
    model.setStatus(entity.getStatus());
    model.setErrorMessage(entity.getErrorMessage());
    model.setRequestedById(entity.getRequestedById());
    model.setSentAt(entity.getSentAt());

    return model;
  }
}
