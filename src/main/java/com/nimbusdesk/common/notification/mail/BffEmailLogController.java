package com.nimbusdesk.common.notification.mail;

import com.nimbussystems.commons.notification.mail.EmailLogEntity;

import com.nimbusdesk.common.security.CheckSecurity;
import com.nimbusdesk.common.web.PageableMapper;
import com.nimbussystems.commons.web.SearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Menu "Configurações &gt; Auditoria de E-mail" - sessão/cookie (chain /bff/**, não JWT), mesmo
 * padrão de BffEmailSettingsController. Permissão própria do app nimbusdesk, seguem seedadas via
 * migration no repo NimbusAuth (EMAIL_LOG_CONSULT) - ver memória "projeto-manage-permission-gap".
 * Só leitura - não existe endpoint de reenvio nem de detalhe por id (o body já vem completo na
 * listagem, ver EmailLogModel.body). Só os envios próprios do NimbusDesk (email_log) - não mescla
 * mais com convite/reset de senha do NimbusAuth (ver EmailLogService.search).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/email/logs")
public class BffEmailLogController {

  /** Sem sort explícito -> mais recentes primeiro, mesmo default do filtro em memória anterior. */
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "sentAt");

  private final EmailLogService emailLogService;
  private final EmailLogModelAssembler modelAssembler;
  private final PagedResourcesAssembler<EmailLogEntity> pagedResourcesAssembler;

  @PostMapping("/search")
  @CheckSecurity.EmailLog.CanConsult
  public PagedModel<EmailLogModel> search(@RequestBody SearchRequest request) {
    Pageable pageable = PageableMapper.toPageable(request.page(), request.size(), request.sort(), DEFAULT_SORT);
    Page<EmailLogEntity> page = emailLogService.search(request, pageable);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }
}
