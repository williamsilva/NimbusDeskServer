package com.nimbusdesk.common.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP interno (machine-to-machine) pro NimbusFlowServer - busca equipamentos atualizados
 * desde um timestamp (ver com.nimbusdesk.tickets.core.EquipamentoSyncService), mesmo mecanismo de
 * secret compartilhado (header X-Internal-Secret) já usado por {@link NimbusAuthInternalClient},
 * agora apontando pro NimbusFlowServer em vez do NimbusAuth (base URL/secret próprios, ver
 * {@link NimbusFlowInternalProperties}).
 *
 * <p><b>Endpoint consumido (GET {@code /internal/v1/equipamentos?atualizadoApos=...}) está sendo
 * criado em paralelo no NimbusFlowServer</b> (PROJECT_SPEC.md) - pode não existir ainda quando este
 * client é chamado; {@link #fetchEquipamentosUpdatedAfter} NÃO engole a exceção (deixa propagar) -
 * quem trata a tolerância a falha (rede/timeout/404/4xx/5xx) é o chamador
 * (EquipamentoSyncService), com um try/catch amplo, exatamente como pedido no PROJECT_SPEC.md.
 *
 * <p>{@link EquipamentoDto} é uma suposição razoável do shape de resposta (campos batendo com
 * {@code EquipamentoRef} local: numeroPatrimonio/descricao/status, mais {@code localizacaoAtual}
 * novo, ver PROJECT_SPEC.md) - o contrato real não pôde ser conferido porque o endpoint ainda não
 * existia no momento desta implementação. {@code @JsonIgnoreProperties(ignoreUnknown = true)}
 * torna a desserialização tolerante a campos extras que o NimbusFlowServer venha a incluir.
 */
@Slf4j
@Service
public class NimbusFlowInternalClient {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EquipamentoDto(
      UUID id,
      String numeroPatrimonio,
      String descricao,
      String localizacaoAtual,
      String status,
      Instant updatedAt) {
  }

  private final RestClient restClient;
  private final String internalApiSecret;

  public NimbusFlowInternalClient(RestClient.Builder restClientBuilder, NimbusFlowInternalProperties props) {
    this.restClient = restClientBuilder.baseUrl(props.getBaseUrl()).build();
    this.internalApiSecret = props.getInternalApiSecret();
  }

  public List<EquipamentoDto> fetchEquipamentosUpdatedAfter(Instant updatedAfter) {
    EquipamentoDto[] result = restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/internal/v1/equipamentos")
            .queryParam("atualizadoApos", updatedAfter)
            .build())
        .header("X-Internal-Secret", internalApiSecret)
        .retrieve()
        .body(EquipamentoDto[].class);

    return result == null ? List.of() : List.of(result);
  }
}
