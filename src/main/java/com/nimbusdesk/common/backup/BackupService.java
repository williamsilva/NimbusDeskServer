package com.nimbusdesk.common.backup;

import com.nimbusdesk.common.security.NimbusCoreInternalClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquestra o backup sob demanda (tela Configurações &gt; Backup): monta um único zip com os
 * alvos pedidos (banco nimbusdesk, banco nimbusauth e/ou bucket de anexos). Um alvo que falhar
 * não derruba os demais - entra como uma linha em erros.txt dentro do próprio zip (mesmo desenho
 * do BackupService do CardsyncServer/NimbusFlowServer, de onde {@code FILES} foi portado em
 * 2026-09-09).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

  private final PgDumpRunner pgDumpRunner;
  private final S3VolumeZipper s3VolumeZipper;
  private final NimbusCoreInternalClient nimbusCoreInternalClient;

  public byte[] execute(List<BackupTarget> targets) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    List<String> errors = new ArrayList<>();

    try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
      if (targets.contains(BackupTarget.NIMBUSDESK_DB)) {
        addEntry(zipOut, "nimbusdesk.dump", "banco nimbusdesk", pgDumpRunner::dump, errors);
      }
      if (targets.contains(BackupTarget.NIMBUSAUTH_DB)) {
        addEntry(zipOut, "nimbusauth.dump", "banco nimbusauth", nimbusCoreInternalClient::fetchDatabaseBackup, errors);
      }
      if (targets.contains(BackupTarget.FILES)) {
        try {
          s3VolumeZipper.zipInto(zipOut, "arquivos");
        } catch (Exception e) {
          log.warn("Falha ao compactar o storage de anexos para o backup: {}", e.getMessage(), e);
          errors.add("Storage de anexos: " + e.getMessage());
        }
      }

      if (!errors.isEmpty()) {
        zipOut.putNextEntry(new ZipEntry("erros.txt"));
        zipOut.write(String.join("\n", errors).getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Falha ao montar o arquivo zip do backup", e);
    }

    return buffer.toByteArray();
  }

  private void addEntry(
      ZipOutputStream zipOut, String fileName, String label, Supplier<byte[]> action, List<String> errors) {
    try {
      byte[] bytes = action.get();
      zipOut.putNextEntry(new ZipEntry(fileName));
      zipOut.write(bytes);
      zipOut.closeEntry();
    } catch (Exception e) {
      log.warn("Falha ao gerar backup de {}: {}", label, e.getMessage(), e);
      errors.add(label + ": " + e.getMessage());
    }
  }
}
