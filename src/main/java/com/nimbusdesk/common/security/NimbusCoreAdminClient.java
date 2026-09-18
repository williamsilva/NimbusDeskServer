package com.nimbusdesk.common.security;

import com.nimbussystems.commons.security.NimbusCoreProxyProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Chamadas server-to-server pro NimbusCore - administração de usuários e grupos (não self-service,
 * ver NimbusCoreClient pra isso). Sempre escopado a app_key="nimbusdesk" nas leituras (grupos e
 * permissões são catalogados por app, ver GroupService no NimbusCore); usuários são globais no
 * NimbusCore (sem app_key), o filtro pro que "pertence ao NimbusDesk" é feito em AdminUserService
 * (pelo appKey dos grupos do usuário), não aqui - este client só espelha o wire format cru.
 *
 * <p>Sem paginação real: /api/v1/users/search é chamado com uma página só (tamanho grande) - o
 * volume esperado de usuários/grupos vinculados ao NimbusDesk é pequeno. Se isso deixar de ser
 * verdade, revisar (ver PAGE_SIZE).
 */
@Slf4j
@Service
public class NimbusCoreAdminClient {

  private static final String APP_KEY = "nimbusdesk";
  private static final int PAGE_SIZE = 500;

  private final RestClient restClient;

  public NimbusCoreAdminClient(RestClient.Builder restClientBuilder, NimbusCoreProxyProperties props) {
    this.restClient = restClientBuilder.baseUrl(props.getBaseUrl()).build();
  }

  // ------------------------- Usuários -------------------------

  /** POST /api/v1/users/search - sem filtro (todos os usuários globais do NimbusCore; o recorte
   *  "vinculado ao NimbusDesk" é aplicado depois, em AdminUserService, pelo appKey dos groups). */
  public List<RawUser> searchAllUsers(String accessToken) {
    try {
      HalPage<RawUser> page = restClient.post()
          .uri("/api/v1/users/search")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new SearchRequest(0, PAGE_SIZE))
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<HalPage<RawUser>>() {});
      return page == null ? List.of() : page.items();
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public RawUser createUser(String accessToken, RawUserInput input) {
    try {
      return restClient.post()
          .uri("/api/v1/users")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(input)
          .retrieve()
          .body(RawUser.class);
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public RawUser getUser(String accessToken, UUID id) {
    try {
      return restClient.get()
          .uri("/api/v1/users/{id}", id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve()
          .body(RawUser.class);
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public RawUser updateUser(String accessToken, UUID id, RawUserInput input) {
    try {
      return restClient.put()
          .uri("/api/v1/users/{id}", id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(input)
          .retrieve()
          .body(RawUser.class);
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public void activateUser(String accessToken, UUID id) {
    postAction(accessToken, "/api/v1/users/{id}/activate", id);
  }

  public void deactivateUser(String accessToken, UUID id) {
    postAction(accessToken, "/api/v1/users/{id}/deactivate", id);
  }

  public void resendInvite(String accessToken, UUID id) {
    postAction(accessToken, "/api/v1/users/{id}/resend-invite", id);
  }

  /** POST /api/v1/users/activate - ListIdsInput ({"ids": [...]}), não confundir com o
   *  activateUser() singular (rota .../{id}/activate). */
  public void activateUsersBulk(String accessToken, List<UUID> ids) {
    bulkAction(accessToken, "/api/v1/users/activate", ids);
  }

  public void deactivateUsersBulk(String accessToken, List<UUID> ids) {
    bulkAction(accessToken, "/api/v1/users/deactivate", ids);
  }

  private void bulkAction(String accessToken, String uri, List<UUID> ids) {
    try {
      restClient.post()
          .uri(uri)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("ids", ids))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  private void postAction(String accessToken, String uri, UUID id) {
    try {
      restClient.post()
          .uri(uri, id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  // ------------------------- Grupos -------------------------

  /** GET /api/v1/groups/options?appKey=nimbusdesk - versão leve (sem contadores/data), usada só
   *  pro multiselect de grupos do formulário de usuário. Pra listagem em si, ver searchAllGroups. */
  public List<RawGroupOption> listGroupOptions(String accessToken) {
    try {
      List<RawGroupOption> options = restClient.get()
          .uri("/api/v1/groups/options?appKey={appKey}", APP_KEY)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<List<RawGroupOption>>() {});
      return options == null ? List.of() : options;
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  /** POST /api/v1/groups/search - filtrado por appKey=nimbusdesk (só grupos do NimbusDesk, ao
   *  contrário de searchAllUsers). Versão completa (com contadores/data/autor) usada na listagem. */
  public List<RawGroup> searchAllGroups(String accessToken) {
    try {
      HalPage<RawGroup> page = restClient.post()
          .uri("/api/v1/groups/search")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new GroupsSearchRequest(0, PAGE_SIZE, new GroupsSearchFilter(APP_KEY)))
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<HalPage<RawGroup>>() {});
      return page == null ? List.of() : page.items();
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public RawGroup getGroup(String accessToken, UUID id) {
    try {
      return restClient.get()
          .uri("/api/v1/groups/{id}", id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve()
          .body(RawGroup.class);
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public RawGroup createGroup(String accessToken, RawGroupInput input) {
    try {
      return restClient.post()
          .uri("/api/v1/groups")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(input)
          .retrieve()
          .body(RawGroup.class);
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public RawGroup updateGroup(String accessToken, UUID id, RawGroupInput input) {
    try {
      return restClient.put()
          .uri("/api/v1/groups/{id}", id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(input)
          .retrieve()
          .body(RawGroup.class);
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public void deleteGroup(String accessToken, UUID id) {
    try {
      restClient.delete()
          .uri("/api/v1/groups/{id}", id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public RawGroup updateGroupPermissions(String accessToken, UUID id, List<UUID> permissionIds) {
    try {
      return restClient.put()
          .uri("/api/v1/groups/{id}/permissions", id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("permissionIds", permissionIds))
          .retrieve()
          .body(RawGroup.class);
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  public RawGroup updateGroupUsers(String accessToken, UUID id, List<UUID> userIds) {
    try {
      return restClient.put()
          .uri("/api/v1/groups/{id}/users", id)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("userIds", userIds))
          .retrieve()
          .body(RawGroup.class);
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  // ------------------------- Permissões -------------------------

  /** GET /api/v1/permissions/options?appKey=nimbusdesk */
  public List<RawPermissionOption> listPermissionOptions(String accessToken) {
    try {
      List<RawPermissionOption> options = restClient.get()
          .uri("/api/v1/permissions/options?appKey={appKey}", APP_KEY)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<List<RawPermissionOption>>() {});
      return options == null ? List.of() : options;
    } catch (RestClientResponseException e) {
      throw mapUpstreamError(e);
    }
  }

  // ------------------------- Wire format (espelha os *Model/*Input do NimbusCore) -------------------------

  public record RawGroupOption(UUID id, String name, String description, String appKey) {}

  public record RawPermissionOption(UUID id, String name, String description, String appKey) {}

  /** Espelha UserMinimalModel do NimbusCore (id/name/userName) - só usado como "quem cadastrou". */
  public record RawUserMinimal(UUID id, String name, String userName) {}

  public record RawGroup(
      UUID id,
      String name,
      String description,
      Integer usersCount,
      Integer permissionsCount,
      Instant createdAt,
      RawUserMinimal createdBy,
      List<RawPermissionOption> permissions,
      List<RawUserMinimal> users) {}

  public record RawGroupInput(String name, String description) {}

  public record RawUser(
      UUID id,
      String name,
      String userName,
      String document,
      Integer status,
      Instant createdAt,
      Instant lastLoginAt,
      Instant blockedUntil,
      Instant passwordExpiresAt,
      RawUserMinimal createdBy,
      List<RawGroupOption> groups) {}

  public record RawUserInput(String userName, String name, String document, List<UUID> groupIds) {}

  private record SearchRequest(int page, int size) {}

  private record GroupsSearchFilter(String appKey) {}

  private record GroupsSearchRequest(int page, int size, GroupsSearchFilter advanced) {}

  /**
   * PagedModel do Spring HATEOAS serializa como {"_embedded": {"content": [...]}, "page": {...}}.
   * Só usado pra /users/search (as listagens de grupo/permissão usam os endpoints /options, que
   * devolvem um array simples, sem esse envelope HAL).
   */
  private record HalPage<T>(@JsonProperty("_embedded") Embedded<T> embedded) {
    private record Embedded<T>(List<T> content) {}

    List<T> items() {
      return embedded == null || embedded.content() == null ? List.of() : embedded.content();
    }
  }

  /** Mesmo mapeamento de erro do NimbusCoreClient (perfil/senha) - repassa o "code" de um 4xx
   *  conhecido, qualquer outra falha vira 502 genérico. */
  private ResponseStatusException mapUpstreamError(RestClientResponseException e) {
    HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
    if (status != null && status.is4xxClientError()) {
      String code = extractErrorCode(e);
      if (code != null) {
        return new ResponseStatusException(status, code);
      }
    }
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NIMBUS_AUTH_ERROR");
  }

  private String extractErrorCode(RestClientResponseException e) {
    try {
      Map<?, ?> body = e.getResponseBodyAs(Map.class);
      Object code = body != null ? body.get("code") : null;
      return code != null ? code.toString() : null;
    } catch (Exception ex) {
      return null;
    }
  }
}
