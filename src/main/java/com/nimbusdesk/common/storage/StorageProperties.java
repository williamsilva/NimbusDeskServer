package com.nimbusdesk.common.storage;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Config do storage de objetos (MinIO local, S3-compatible) - portado de com.nimbusflow.common.
 * storage.StorageProperties, com 3 ajustes de decisão de design pro domínio de anexo de Chamado
 * (diferente de mídia de Medição/Equipamento do NimbusFlow):
 * <ul>
 *   <li>{@code bucket} default "nimbusdesk-attachments" (bucket próprio, ver docker-compose.yml);</li>
 *   <li>{@code allowedContentTypes} ampliado pra cobrir documento comum de chamado de TI (PDF/
 *   Office/texto/zip), além de imagem - anexo de Chamado não é só evidência fotográfica;</li>
 *   <li>{@code presignedUrlTtl} default 5 minutos (era 15) - PROJECT_SPEC.md pede TTL curto pra
 *   URL de anexo de Chamado.</li>
 * </ul>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

  @NotBlank
  private String endpoint;

  @NotBlank
  private String accessKey;

  @NotBlank
  private String secretKey;

  @NotBlank
  private String bucket;

  /** MinIO ignora o valor, mas o AWS SDK exige uma região válida. */
  private String region = "us-east-1";

  private int maxUploadSizeMb = 25;

  private List<String> allowedContentTypes = List.of(
      "image/jpeg", "image/png", "image/webp", "image/gif",
      "application/pdf",
      "application/msword",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.ms-excel",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "text/plain", "text/csv",
      "application/zip");

  private Duration presignedUrlTtl = Duration.ofMinutes(5);
}
