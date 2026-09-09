package com.nimbusdesk.common.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Portado byte-a-byte de com.nimbusflow.common.storage.S3StorageService - implementação
 *  S3-compatible (MinIO local, dev/prod) pros anexos de Chamado. */
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final StorageProperties props;

  @Override
  public String upload(String key, MultipartFile file) {
    try {
      PutObjectRequest request = PutObjectRequest.builder()
          .bucket(props.getBucket())
          .key(key)
          .contentType(file.getContentType())
          .build();
      s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
      return key;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read upload content for key " + key, e);
    }
  }

  @Override
  public URL presignedGetUrl(String key) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(props.getBucket())
        .key(key)
        .build();

    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(props.getPresignedUrlTtl())
        .getObjectRequest(getObjectRequest)
        .build();

    return s3Presigner.presignGetObject(presignRequest).url();
  }
}
