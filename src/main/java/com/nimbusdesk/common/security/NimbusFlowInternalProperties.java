package com.nimbusdesk.common.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Base URL do NimbusFlowServer alcançável PELO BACKEND + secret compartilhado (header
 * X-Internal-Secret) pra chamar a API interna machine-to-machine dele ({@code GET
 * /internal/v1/equipamentos}, ver NimbusFlowInternalClient/EquipamentoSyncService). Mesmo padrão
 * de {@code com.nimbussystems.commons.security.NimbusCoreProxyProperties}, mas local ao NimbusDesk
 * (não faz parte da lib compartilhada nimbus-commons-server - é uma integração exclusiva
 * NimbusDesk-&gt;NimbusFlow, sem uso nos outros apps consumidores da lib).
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "nimbus.nimbusflow")
public class NimbusFlowInternalProperties {

  @NotBlank
  private String baseUrl;

  @NotBlank
  private String internalApiSecret;
}
