package com.nimbusdesk.common.storage;

import java.net.URL;
import org.springframework.web.multipart.MultipartFile;

/** Portado de com.nimbusflow.common.storage (StorageService) - mesma interface, sem mudança. */
public interface StorageService {

  /** Sobe o arquivo pra key informada e retorna a mesma key (echo, útil pra encadear chamadas). */
  String upload(String key, MultipartFile file);

  /** URL assinada e temporária (TTL de StorageProperties.presignedUrlTtl) pra leitura do objeto. */
  URL presignedGetUrl(String key);
}
