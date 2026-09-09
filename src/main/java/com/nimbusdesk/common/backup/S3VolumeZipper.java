package com.nimbusdesk.common.backup;

import com.nimbusdesk.common.storage.StorageProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Compacta todo o bucket de anexos (MinIO local / R2 em produção - ver
 * StorageProperties#getBucket, "nimbusdesk-attachments") num único zip, sem baixar tudo pra disco
 * primeiro - portado byte-a-byte de com.nimbusflow.common.backup.S3VolumeZipper (mesma técnica já
 * usada lá desde 2026-09-07, o NimbusDesk só não tinha esse alvo ainda no backup sob demanda, ver
 * BackupTarget/BackupService). Sem filesystem local pra iterar (diferente do FileVolumeZipper do
 * CardsyncServer, que usa {@code Files.walk} numa pasta local) - aqui itera o bucket via
 * {@code listObjectsV2Paginator} (achata a paginação automaticamente) e copia cada objeto em
 * streaming pro ZipOutputStream já aberto.
 */
@Component
@RequiredArgsConstructor
public class S3VolumeZipper {

  private final S3Client s3Client;
  private final StorageProperties storageProperties;

  public void zipInto(ZipOutputStream zipOut, String entryPrefix) {
    String bucket = storageProperties.getBucket();

    Iterable<S3Object> objects = s3Client
        .listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).build())
        .contents();

    for (S3Object object : objects) {
      try (ResponseInputStream<GetObjectResponse> objectStream = s3Client.getObject(
          GetObjectRequest.builder().bucket(bucket).key(object.key()).build())) {
        zipOut.putNextEntry(new ZipEntry(entryPrefix + "/" + object.key()));
        objectStream.transferTo(zipOut);
        zipOut.closeEntry();
      } catch (IOException e) {
        throw new UncheckedIOException("Falha ao compactar objeto do storage: " + object.key(), e);
      }
    }
  }
}
