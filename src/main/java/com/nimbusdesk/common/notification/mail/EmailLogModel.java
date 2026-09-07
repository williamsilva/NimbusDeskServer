package com.nimbusdesk.common.notification.mail;

import com.nimbussystems.commons.notification.mail.EmailLogStatus;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/** Usado só pela listagem (não há endpoint de detalhe/create/update - a tela só lê, ver
 *  {@link BffEmailLogController}) - por isso não existe um record "Response" paralelo pra manter;
 *  era um record simples antes, agora é RepresentationModel direto. */
@Getter
@Setter
@NoArgsConstructor
@Relation(collectionRelation = "content")
public class EmailLogModel extends RepresentationModel<EmailLogModel> {

  private UUID id;
  private String eventType;
  private String recipients;
  private String subject;
  private String template;
  private String body;
  private EmailLogStatus status;
  private String errorMessage;
  private String requestedById;
  private Instant sentAt;
}
