package com.nimbusdesk.common.backup;

import com.nimbussystems.commons.security.CurrentUserProvider;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Menu "Configurações &gt; Backup" - sessão/cookie (chain /bff/**, não JWT), mesmo padrão de
 * BffEmailSettingsController. Permissão própria do app nimbusdesk (BACKUP_PROCESS), seedada via
 * migration no repo NimbusCore (fora deste repo). Portado de com.nimbusflow.common.backup
 * (NimbusFlowServer) em 2026-09-07 - ver BackupTarget para a diferença de escopo (sem FILES/S3).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/settings/backup")
public class BffBackupController {

  private static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private final BackupService backupService;
  private final CurrentUserProvider currentUserProvider;
  private final Clock clock;

  @PostMapping("/execute")
  public ResponseEntity<Resource> execute(@Valid @RequestBody BackupExecuteRequest request) {
    requireAuthority("BACKUP_PROCESS");
    byte[] zip = backupService.execute(request.targets());

    // Nome sempre ASCII (prefixo fixo + timestamp) - evita divergência entre o encoding RFC 2047
    // que o Spring geraria pra um nome com acento e o parser em JS do frontend (blob de resposta
    // de POST, não uma URL navegável), mesmo racional do BackupController do CardsyncServer.
    String filename = "nimbusdesk-backup-" + LocalDateTime.now(clock).format(FILENAME_TIMESTAMP) + ".zip";
    ContentDisposition disposition = ContentDisposition.attachment().filename(filename).build();

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(new ByteArrayResource(zip));
  }

  /** @param permission nome cru (ex.: "BACKUP_PROCESS") - authorities já vêm prefixadas "PERM_". */
  private void requireAuthority(String permission) {
    if (!currentUserProvider.hasAuthority("PERM_" + permission)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing " + permission + " authority");
    }
  }
}
