package com.nimbusdesk.common.notification.mail;

import com.nimbussystems.commons.notification.mail.EmailLogStatus;

import com.nimbussystems.commons.notification.mail.EmailLogEntity;

import com.nimbussystems.commons.web.FilterSupport;
import com.nimbussystems.commons.web.SearchRequest;
import com.nimbusdesk.common.web.spec.BaseSpecificationSupport;
import com.nimbusdesk.common.web.spec.Specs;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Specification de filtro/ordenação/paginação da Auditoria de E-mail (ver
 *  {@link BaseSpecificationSupport} para os helpers reaproveitáveis). */
@Component
public class EmailLogSpecs extends BaseSpecificationSupport<EmailLogEntity> {

  public Specification<EmailLogEntity> fromRequest(SearchRequest request) {
    var tableFilters = request.tableFilters();
    var advanced = request.advanced();

    String recipients = FilterSupport.textFilter(tableFilters, advanced, "recipients", "recipients");
    String subject = FilterSupport.textFilter(tableFilters, advanced, "subject", "subject");
    List<String> eventTypes = FilterSupport.listFilter(tableFilters, advanced, "eventType", "eventType");
    List<String> statuses = FilterSupport.listFilter(tableFilters, advanced, "status", "status");
    Instant[] sentAtRange = FilterSupport.periodInstantRange(tableFilters, advanced, "sentAt", "periodSentAt", "sentAt");

    Specification<EmailLogEntity> spec = Specs.all();
    spec = spec.and(contains(recipients, "recipients"));
    spec = spec.and(contains(subject, "subject"));
    spec = spec.and(inCodes("eventType", eventTypes, e -> e));
    spec = spec.and(inCodes("status", statuses, this::statusFromName));
    spec = spec.and(rangeInstant("sentAt", sentAtRange == null ? null : sentAtRange[0],
        sentAtRange == null ? null : sentAtRange[1]));

    return spec;
  }

  private EmailLogStatus statusFromName(String name) {
    try {
      return EmailLogStatus.valueOf(name);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
