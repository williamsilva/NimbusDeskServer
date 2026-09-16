package com.nimbusdesk.common.notification.mail;

import com.nimbussystems.commons.notification.mail.EmailDeliveryLogger;

import com.nimbussystems.commons.notification.mail.EmailLogStatus;

import com.nimbussystems.commons.notification.mail.EmailLogEntity;

import com.nimbussystems.commons.notification.mail.EmailSenderService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Grava o log de envio/erro (email_log) - a listagem/busca (antes exposta em
 *  BffEmailLogController/EmailLogModel/EmailLogSpecs, com a auditoria federada e centralizada no
 *  NimbusAuthWeb - ver InternalEmailLogController) foi removida junto com a tela local (Fase 5 da
 *  consolidação de Segurança). */
@Service
@RequiredArgsConstructor
public class EmailLogService implements EmailDeliveryLogger {

  private final EmailLogRepository repository;

  /** REQUIRES_NEW - um chamador futuro pode disparar o envio de dentro de uma transação
   *  @Transactional(readOnly = true) (ex.: um job agendado que lê e notifica) - sem propagação própria,
   *  este save() participa dessa transação de leitura e é descartado silenciosamente no commit
   *  (Hibernate nem chega a fazer flush de uma sessão só-leitura) - o e-mail sai de verdade, mas a
   *  auditoria em email_log fica muda, sem nenhum erro visível. Mesmo motivo de logError abaixo. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logSent(EmailSenderService.Message message, String body) {
    repository.save(EmailLogEntity.builder()
        .eventType(message.getEventType())
        .recipients(joinRecipients(message))
        .subject(message.getSubject())
        .template(message.getTemplate())
        .body(body)
        .status(EmailLogStatus.SENT)
        .requestedById(message.getRequestedById())
        .build());
  }

  /** REQUIRES_NEW - mesmo motivo do EmailLogService.logError no NimbusAuth: o registro do erro
   *  precisa sobreviver mesmo que a transação que tentou enviar o e-mail seja revertida depois. */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logError(EmailSenderService.Message message, String body, Exception ex) {
    repository.save(EmailLogEntity.builder()
        .eventType(message.getEventType())
        .recipients(joinRecipients(message))
        .subject(message.getSubject())
        .template(message.getTemplate())
        .body(body)
        .status(EmailLogStatus.FAILED)
        .errorMessage(truncate(ex.getMessage(), 1000))
        .requestedById(message.getRequestedById())
        .build());
  }

  private String joinRecipients(EmailSenderService.Message message) {
    if (message.getRecipients() == null || message.getRecipients().isEmpty()) {
      return "unknown";
    }
    return String.join(", ", message.getRecipients());
  }

  private String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }
}
