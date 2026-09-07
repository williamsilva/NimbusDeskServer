package com.nimbusdesk.common.security;

import com.nimbussystems.commons.security.RemoteUserLookupClient;
import com.nimbussystems.commons.security.RemoteUserSummary;

import com.nimbussystems.commons.security.NimbusAuthProxyProperties;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP interno (machine-to-machine) pro NimbusAuth - resolve nome/username de usuários e
 * pede o dump do banco do NimbusAuth pro backup sob demanda (ver
 * com.nimbusdesk.common.backup.BackupService). Mesmo papel do NimbusAuthInternalClient do
 * CardsyncServer/NimbusFlowServer/NimbusNovaxServer, mesmos endpoints (/internal/**) e mesmo
 * secret compartilhado (NIMBUS_INTERNAL_API_SECRET) - autenticado por header, não por token de
 * usuário.
 *
 * <p>Implementa {@link RemoteUserLookupClient} (só {@code fetchUsers} - único método com 1
 * chamador em todo o app, o {@code UserDirectoryService} compartilhado, extraído em 2026-09-07,
 * embora hoje sem nenhum chamador ativo neste app - ver README do NimbusCommonsServer). Os outros
 * métodos abaixo continuam retornando o {@code UserSummary} local (mesmo shape de
 * {@link RemoteUserSummary}, mas usado direto por código de domínio que não faz parte desta
 * extração).
 */
@Slf4j
@Service
public class NimbusAuthInternalClient implements RemoteUserLookupClient {

  public record UserSummary(UUID id, String username, String name) {
  }

  private final RestClient restClient;
  private final String internalApiSecret;

  public NimbusAuthInternalClient(RestClient.Builder restClientBuilder, NimbusAuthProxyProperties props) {
    this.restClient = restClientBuilder.baseUrl(props.getBaseUrl()).build();
    this.internalApiSecret = props.getInternalApiSecret();
  }

  /** Degrada silenciosamente (lista vazia) em qualquer falha - resolver nome de usuário é só
   *  contexto de exibição, nunca deve derrubar a listagem de aditivos/medições/parcelas. */
  @Override
  public List<RemoteUserSummary> fetchUsers(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }

    try {
      RemoteUserSummary[] result = restClient.get()
          .uri(uriBuilder -> uriBuilder.path("/internal/users").queryParam("ids", ids).build())
          .header("X-Internal-Secret", internalApiSecret)
          .retrieve()
          .body(RemoteUserSummary[].class);

      return result != null ? List.of(result) : List.of();
    } catch (Exception e) {
      log.warn("Falha ao resolver usuários no NimbusAuth: {}", e.getMessage());
      return List.of();
    }
  }

  /** Lista leve dos usuários vinculados a algum grupo do app_key informado (ver
   *  GET /internal/users/options no NimbusAuth) - autenticado só pelo secret compartilhado, não
   *  pelo token do usuário chamador, então não exige USERS_CONSULT (ver AdminUserService.options
   *  /AdminUserService.optionsFilter) nem expõe o diretório global de outros apps Nimbus (ao
   *  contrário de GET /api/v1/users/options, que é global de propósito). Não degrada
   *  silenciosamente (ao contrário de fetchUsers acima) - é a fonte de dados do seletor "escolha
   *  um usuário" de Chamados/Departamentos/Usuários, uma falha aqui deve aparecer como erro pro
   *  chamador, não uma lista vazia silenciosa que pareceria "nenhum usuário cadastrado". */
  public List<UserSummary> fetchOptionsByAppKey(String appKey) {
    UserSummary[] result = restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/internal/users/options").queryParam("appKey", appKey).build())
        .header("X-Internal-Secret", internalApiSecret)
        .retrieve()
        .body(UserSummary[].class);

    return result != null ? List.of(result) : List.of();
  }

  /** Usuários (do app_key informado) que têm a permissão pedida (ver GET
   *  /internal/users/permissions no NimbusAuth) - pra resolver destinatários de notificação a
   *  partir de quem tem uma permissão, em vez de uma lista de e-mails configurada manualmente e
   *  sujeita a ficar desincronizada do catálogo real de usuários/grupos (mesmo padrão usado no
   *  NimbusFlowServer/NimbusNovaxServer). Não degrada silenciosamente (mesmo critério de
   *  {@link #fetchOptionsByAppKey}) - o chamador decide como reagir a uma falha. */
  public List<UserSummary> fetchOptionsByPermission(String appKey, String permission) {
    UserSummary[] result = restClient.get()
        .uri(uriBuilder -> uriBuilder.path("/internal/users/permissions")
            .queryParam("appKey", appKey)
            .queryParam("permission", permission)
            .build())
        .header("X-Internal-Secret", internalApiSecret)
        .retrieve()
        .body(UserSummary[].class);

    return result != null ? List.of(result) : List.of();
  }

  /** Pede ao NimbusAuth um dump (pg_dump formato custom) do próprio banco dele - usado pelo
   *  backup sob demanda (com.nimbusdesk.common.backup.BackupService). O NimbusAuth não expõe suas
   *  credenciais de banco pra fora; quem sabe rodar o pg_dump é ele mesmo, aqui só recebemos os
   *  bytes prontos (ver GET /internal/backup/database no NimbusAuth). NÃO degrada silenciosamente
   *  (ao contrário de fetchUsers acima) - o chamador precisa saber que este alvo específico falhou
   *  para reportar no zip final (erros.txt), em vez de produzir um backup incompleto sem avisar. */
  public byte[] fetchDatabaseBackup() {
    byte[] result = restClient.get()
        .uri("/internal/backup/database")
        .header("X-Internal-Secret", internalApiSecret)
        .retrieve()
        .body(byte[].class);

    if (result == null || result.length == 0) {
      throw new IllegalStateException("NimbusAuth retornou um backup vazio");
    }
    return result;
  }
}
