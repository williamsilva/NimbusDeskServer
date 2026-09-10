package com.nimbusdesk.common.backup;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** API interna machine-to-machine (rota /internal/backup/**, ver InternalBackupSecretFilter) -
 *  consumida pelo NimbusAuth pra compor o backup centralizado do ecossistema Nimbus. Espelha o
 *  InternalBackupController já existente no NimbusAuthServer (que este próprio app já consome
 *  hoje via NimbusAuthInternalClient.fetchDatabaseBackup() pro backup manual/BFF) - agora na
 *  direção inversa. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InternalBackupController {

  private final PgDumpRunner pgDumpRunner;
  private final S3VolumeZipper s3VolumeZipper;

  @GetMapping("/internal/backup/database")
  public ResponseEntity<byte[]> database() {
    log.info("Backup do banco NimbusDesk solicitado via API interna.");
    byte[] dump = pgDumpRunner.dump();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(dump);
  }

  @GetMapping("/internal/backup/files")
  public void files(HttpServletResponse response) throws IOException {
    log.info("Backup de arquivos NimbusDesk solicitado via API interna.");
    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nimbusdesk-files.zip\"");

    try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream())) {
      s3VolumeZipper.zipInto(zipOut, "arquivos");
    }
  }
}
