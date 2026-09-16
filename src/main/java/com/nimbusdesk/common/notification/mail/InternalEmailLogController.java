package com.nimbusdesk.common.notification.mail;

import com.nimbussystems.commons.notification.mail.EmailLogEntity;
import com.nimbussystems.commons.notification.mail.EmailLogStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API interna machine-to-machine (rota /internal/email-log/**, ver InternalBackupSecretFilter/
 *  internalEmailLogChain em SecurityConfig) - consumida pelo NimbusAuth pra federar a tela central
 *  de Auditoria de E-mail. Especification própria (não reaproveita EmailLogSpecs/SearchRequest,
 *  que assumem o formato de filtro do PrimeNG vindo do BFF) - contrato simples de query params,
 *  direto no repositório (que já suporta Specification via JpaSpecificationExecutor). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/email-log")
public class InternalEmailLogController {

  private final EmailLogRepository repository;

  @GetMapping("/search")
  public PageModel search(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String recipient,
      @RequestParam(required = false) String subject,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sentAtFrom,
      @RequestParam(required = false) String sentAtTo) {

    Instant from = parseInstant(sentAtFrom);
    Instant to = parseInstant(sentAtTo);
    EmailLogStatus statusEnum = parseStatus(status);

    Specification<EmailLogEntity> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (recipient != null && !recipient.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("recipients")), "%" + recipient.toLowerCase(Locale.ROOT) + "%"));
      }
      if (subject != null && !subject.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("subject")), "%" + subject.toLowerCase(Locale.ROOT) + "%"));
      }
      if (eventType != null && !eventType.isBlank()) {
        predicates.add(cb.equal(root.get("eventType"), eventType));
      }
      if (statusEnum != null) {
        predicates.add(cb.equal(root.get("status"), statusEnum));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("sentAt"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };

    Pageable pageable = PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : size, Sort.by(Sort.Direction.DESC, "sentAt"));
    Page<EmailLogEntity> result = repository.findAll(spec, pageable);
    List<ItemModel> content = result.getContent().stream().map(InternalEmailLogController::toItem).toList();

    return new PageModel(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  private static EmailLogStatus parseStatus(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return EmailLogStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Instant parseInstant(String value) {
    return value == null || value.isBlank() ? null : Instant.parse(value);
  }

  private static ItemModel toItem(EmailLogEntity e) {
    return new ItemModel(
        e.getRecipients(),
        e.getSubject(),
        e.getTemplate(),
        e.getStatus() == null ? null : e.getStatus().name(),
        e.getEventType(),
        e.getErrorMessage(),
        e.getSentAt() == null ? null : e.getSentAt().toString());
  }

  public record ItemModel(
      String recipients, String subject, String template, String status, String eventType,
      String errorMessage, String sentAt) {}

  public record PageModel(List<ItemModel> content, int number, int size, long totalElements, int totalPages) {}
}
