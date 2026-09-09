package com.nimbusdesk.common.security.bff.admin;

import com.nimbussystems.commons.security.bff.admin.AdminUserResponse;

import com.nimbussystems.commons.security.bff.admin.AdminUserRequest;

import com.nimbussystems.commons.security.bff.admin.AdminUserMinimalResponse;

import com.nimbussystems.commons.security.bff.admin.AdminSearchRequest;

import com.nimbussystems.commons.security.bff.admin.AdminGroupOptionResponse;

import com.nimbussystems.commons.security.bff.admin.AdminFilterSupport;

import com.nimbusdesk.common.security.NimbusAuthAdminClient;
import com.nimbusdesk.common.security.NimbusAuthAdminClient.RawGroupOption;
import com.nimbusdesk.common.security.NimbusAuthAdminClient.RawUser;
import com.nimbusdesk.common.security.NimbusAuthAdminClient.RawUserInput;
import com.nimbusdesk.common.security.NimbusAuthInternalClient;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Administração de usuários (menu Segurança) - camada de autorização (defesa em profundidade,
 * mesmo padrão de SupplierService: a checagem real também é feita pelo próprio NimbusAuth via as
 * permissões PERM_USERS_* no token repassado, isto aqui só evita a chamada de rede desnecessária e
 * falha mais rápido/com mensagem clara) + a regra de "usuário é global no NimbusAuth" (ver
 * update()).
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

  private static final String NIMBUSDESK_APP_KEY = "nimbusdesk";

  /** Usuário bootstrap global do NimbusAuth (UUID fixo 000...0001, criado por migration lá) - o
   *  próprio NimbusAuth já filtra esse username em {@code GET /api/v1/users/search} (usado por
   *  #list/#search aqui, via NimbusAuthAdminClient#searchAllUsers), mas NÃO filtra em
   *  {@code GET /internal/users/options} (usado só por #options, endpoint machine-to-machine que
   *  não passa pelo mesmo UserService#list do NimbusAuth) - por isso #options precisa do próprio
   *  filtro aqui. Mesmo critério usado lá (username fixo, normalizado com trim+lowercase) - não é
   *  flag de banco, é convenção; NimbusFlow/NimbusNovax têm essa mesma lacuna hoje (não corrigida
   *  aqui de propósito, fora do escopo pedido). */
  private static final String SUPPORT_USER_NAME = "suporte@nimbussystems.com.br";

  /** Espelha USER_STATUS_CODE_MAP do frontend (user-status.enum.ts) - o status cru do NimbusAuth
   *  é um Integer, mas os filtros (coluna e painel avançado) mandam os nomes do enum. */
  private static final Map<Integer, String> USER_STATUS_NAMES = Map.of(
      0, "NULL", 1, "ACTIVE", 2, "INACTIVE", 3, "BLOCKED", 4, "DISABLED", 5, "PENDING_PASSWORD");

  private final NimbusAuthAdminClient client;
  private final NimbusAuthInternalClient internalClient;

  /** Só usuários com pelo menos um grupo do NimbusDesk - usuários são globais no NimbusAuth (sem
   *  app_key própio), então a busca crua traz todo mundo (inclusive de outros apps Nimbus, ex.:
   *  Cardsync); decisão explícita do usuário (2026-08-05): não expor esse diretório completo aqui. */
  public List<AdminUserResponse> list(String accessToken) {
    return client.searchAllUsers(accessToken).stream()
        .filter(this::belongsToNimbusDesk)
        .map(this::toResponse)
        .toList();
  }

  public AdminUserResponse get(String accessToken, UUID id) {
    RawUser user = client.getUser(accessToken, id);
    if (!belongsToNimbusDesk(user)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    return toResponse(user);
  }

  /** Lista leve (id/name/userName) pros seletores de usuário - mesmo recorte de list() (só
   *  vinculados ao NimbusDesk), mas usada por QUALQUER usuário autenticado (ex.: seletor de
   *  responsável em Chamados/Departamentos), não só quem tem USERS_CONSULT - por isso busca via
   *  API interna (secret compartilhado, já escopada por app_key no próprio NimbusAuth) em vez de
   *  reaproveitar client.searchAllUsers (que repassa o token do usuário chamador pro
   *  POST /api/v1/users/search administrativo do NimbusAuth, exigindo USERS_CONSULT de verdade -
   *  bug real: usuário com CHAMADO_CONSULT mas sem USERS_CONSULT via /forbidden ao abrir
   *  Chamados). */
  public List<AdminUserMinimalResponse> options() {
    return internalClient.fetchOptionsByAppKey(NIMBUSDESK_APP_KEY).stream()
        .filter(u -> !isSupportUser(u.username()))
        .map(u -> new AdminUserMinimalResponse(u.id(), u.name(), u.username()))
        .sorted(Comparator.comparing(AdminUserMinimalResponse::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private static boolean isSupportUser(String username) {
    return username != null && username.trim().equalsIgnoreCase(SUPPORT_USER_NAME);
  }

  /**
   * Sem paginação/filtro real no NimbusAuth pra esse recorte (ver NimbusAuthAdminClient) - busca
   * tudo (volume esperado é pequeno, mesma premissa do client) e filtra/ordena/pagina em memória;
   * embrulhado numa {@link PageImpl} só pra reaproveitar o mesmo envelope {@code PagedModel} das
   * demais telas (ver {@link BffAdminUsersController#search}) - sem Specification/JPA possível
   * aqui, o dado é remoto (NimbusAuth via HTTP).
   */
  public Page<AdminUserResponse> search(String accessToken, AdminSearchRequest request) {
    List<AdminUserResponse> all = client.searchAllUsers(accessToken).stream()
        .filter(this::belongsToNimbusDesk)
        .map(this::toResponse)
        .toList();

    List<AdminUserResponse> sorted = sortUsers(filterUsers(all, request), request);

    int page = request.page() == null ? 0 : Math.max(0, request.page());
    int size = request.size() == null || request.size() <= 0 ? 20 : request.size();
    int from = Math.min(page * size, sorted.size());
    int to = Math.min(from + size, sorted.size());

    Pageable pageable = PageRequest.of(page, size);
    return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
  }

  private List<AdminUserResponse> filterUsers(List<AdminUserResponse> items, AdminSearchRequest request) {
    Map<String, Object> tableFilters = request.tableFilters();
    Map<String, Object> advanced = request.advanced();

    String name = AdminFilterSupport.textFilter(tableFilters, advanced, "name", "name");
    String userName = AdminFilterSupport.textFilter(tableFilters, advanced, "userName", "userName");
    String document = AdminFilterSupport.textFilter(tableFilters, advanced, "document", "document");
    List<String> statuses = AdminFilterSupport.listFilter(tableFilters, advanced, "status", "status");
    List<String> createdByIds = AdminFilterSupport.listFilter(tableFilters, advanced, "createdBy", "createdBy");
    Instant[] createdAtRange = AdminFilterSupport.periodInstantRange(
        tableFilters, advanced, "createdAt", "periodCreatedAt", "createdAt");
    Instant[] lastLoginAtRange = AdminFilterSupport.periodInstantRange(
        tableFilters, advanced, "lastLoginAt", "periodLastLoginAt", "lastLoginAt");
    Instant[] blockedUntilRange = AdminFilterSupport.periodInstantRange(
        tableFilters, advanced, "blockedUntil", "periodBlockedUntil", "blockedUntil");
    Instant[] passwordExpiresAtRange = AdminFilterSupport.periodInstantRange(
        tableFilters, advanced, "passwordExpiresAt", "periodPasswordExpiresAt", "passwordExpiresAt");
    String global = request.globalFilter();

    return items.stream()
        .filter(u -> AdminFilterSupport.containsIgnoreCase(u.name(), name))
        .filter(u -> AdminFilterSupport.containsIgnoreCase(u.userName(), userName))
        .filter(u -> AdminFilterSupport.containsIgnoreCase(u.document(), document))
        .filter(u -> statuses.isEmpty() || statuses.contains(USER_STATUS_NAMES.getOrDefault(u.status(), "NULL")))
        .filter(u -> createdByIds.isEmpty()
            || (u.createdBy() != null && createdByIds.contains(String.valueOf(u.createdBy().id()))))
        .filter(u -> AdminFilterSupport.withinRange(u.createdAt(), createdAtRange))
        .filter(u -> AdminFilterSupport.withinRange(u.lastLoginAt(), lastLoginAtRange))
        .filter(u -> AdminFilterSupport.withinRange(u.blockedUntil(), blockedUntilRange))
        .filter(u -> AdminFilterSupport.withinRange(u.passwordExpiresAt(), passwordExpiresAtRange))
        .filter(u -> global == null || global.isBlank()
            || AdminFilterSupport.containsIgnoreCase(u.name(), global)
            || AdminFilterSupport.containsIgnoreCase(u.userName(), global)
            || AdminFilterSupport.containsIgnoreCase(u.document(), global))
        .toList();
  }

  private List<AdminUserResponse> sortUsers(List<AdminUserResponse> items, AdminSearchRequest request) {
    AdminSearchRequest.SortItem sortItem =
        (request.sort() == null || request.sort().isEmpty()) ? null : request.sort().get(0);
    String field = sortItem != null && sortItem.field() != null ? sortItem.field() : "name";
    boolean desc = sortItem != null && sortItem.order() != null && sortItem.order() < 0;

    Comparator<AdminUserResponse> comparator = switch (field) {
      case "userName" -> Comparator.comparing(u -> orEmpty(u.userName()), String.CASE_INSENSITIVE_ORDER);
      case "document" -> Comparator.comparing(u -> orEmpty(u.document()), String.CASE_INSENSITIVE_ORDER);
      case "status" -> Comparator.comparing(u -> u.status() == null ? -1 : u.status());
      case "lastLoginAt" -> Comparator.comparing(u -> orEpoch(u.lastLoginAt()));
      case "blockedUntil" -> Comparator.comparing(u -> orEpoch(u.blockedUntil()));
      case "passwordExpiresAt" -> Comparator.comparing(u -> orEpoch(u.passwordExpiresAt()));
      case "createdAt" -> Comparator.comparing(u -> orEpoch(u.createdAt()));
      case "createdBy" -> Comparator.comparing(
          u -> u.createdBy() == null ? "" : orEmpty(u.createdBy().name()), String.CASE_INSENSITIVE_ORDER);
      default -> Comparator.comparing(u -> orEmpty(u.name()), String.CASE_INSENSITIVE_ORDER);
    };

    if (desc) {
      comparator = comparator.reversed();
    }

    return items.stream().sorted(comparator).toList();
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  private static Instant orEpoch(Instant value) {
    return value == null ? Instant.EPOCH : value;
  }

  /**
   * Usuário é global no NimbusAuth (sem app_key própio) - se o e-mail já existir (criado por outro
   * app Nimbus, ex.: Cardsync), POST /api/v1/users falharia com 409 (USER_USERNAME_ALREADY_EXISTS),
   * mesmo o usuário não tendo nenhum grupo do NimbusDesk ainda. "Cadastrar" nesse caso não é criar
   * um usuário novo, é conceder acesso ao NimbusDesk a um usuário que já existe - mesmo caminho de
   * merge de grupos que update() já usa, preservando name/document/userName atuais (o formulário de
   * cadastro não deve sobrescrever a identidade de um usuário que já existe em outro app).
   */
  public AdminUserResponse create(String accessToken, AdminUserRequest request) {
    RawUser existing = client.searchAllUsers(accessToken).stream()
        .filter(u -> u.userName() != null && u.userName().equalsIgnoreCase(request.userName()))
        .findFirst()
        .orElse(null);

    if (existing != null) {
      return grantAccessToExisting(accessToken, existing, request);
    }

    RawUserInput input = new RawUserInput(
        request.userName(), request.name(), request.document(), List.copyOf(request.groupIds()));
    return toResponse(client.createUser(accessToken, input));
  }

  private AdminUserResponse grantAccessToExisting(
      String accessToken, RawUser existing, AdminUserRequest request) {
    Set<UUID> finalGroupIds = new LinkedHashSet<>();
    if (existing.groups() != null) {
      existing.groups().stream().map(RawGroupOption::id).forEach(finalGroupIds::add);
    }
    finalGroupIds.addAll(request.groupIds());

    RawUserInput input = new RawUserInput(
        existing.userName(), existing.name(), existing.document(), List.copyOf(finalGroupIds));
    return toResponse(client.updateUser(accessToken, existing.id(), input));
  }

  /**
   * PUT /api/v1/users/{id} faz replace total dos grupos do usuário (ver UserService.update no
   * NimbusAuth) - se mandássemos só os groupIds do formulário (só grupos do NimbusDesk), qualquer
   * grupo de outro app Nimbus que esse usuário já tivesse seria silenciosamente removido. Busca o
   * estado atual, preserva os grupos de fora do NimbusDesk intactos, funde com a nova seleção.
   */
  public AdminUserResponse update(String accessToken, UUID id, AdminUserRequest request) {
    RawUser current = client.getUser(accessToken, id);
    Set<UUID> finalGroupIds = new LinkedHashSet<>();
    if (current.groups() != null) {
      current.groups().stream()
          .filter(g -> !NIMBUSDESK_APP_KEY.equals(g.appKey()))
          .map(RawGroupOption::id)
          .forEach(finalGroupIds::add);
    }
    finalGroupIds.addAll(request.groupIds());

    RawUserInput input = new RawUserInput(
        request.userName(), request.name(), request.document(), List.copyOf(finalGroupIds));
    return toResponse(client.updateUser(accessToken, id, input));
  }

  public void activate(String accessToken, UUID id) {
    client.activateUser(accessToken, id);
  }

  public void deactivate(String accessToken, UUID id) {
    client.deactivateUser(accessToken, id);
  }

  public void resendInvite(String accessToken, UUID id) {
    client.resendInvite(accessToken, id);
  }

  public void activateBulk(String accessToken, List<UUID> ids) {
    client.activateUsersBulk(accessToken, ids);
  }

  public void deactivateBulk(String accessToken, List<UUID> ids) {
    client.deactivateUsersBulk(accessToken, ids);
  }

  private boolean belongsToNimbusDesk(RawUser user) {
    return user.groups() != null && user.groups().stream().anyMatch(g -> NIMBUSDESK_APP_KEY.equals(g.appKey()));
  }

  private AdminUserResponse toResponse(RawUser u) {
    List<AdminGroupOptionResponse> nimbusDeskGroups = u.groups() == null
        ? List.of()
        : u.groups().stream()
            .filter(g -> NIMBUSDESK_APP_KEY.equals(g.appKey()))
            .map(g -> new AdminGroupOptionResponse(g.id(), g.name(), g.description()))
            .collect(Collectors.toList());

    AdminUserMinimalResponse createdBy = u.createdBy() == null
        ? null
        : new AdminUserMinimalResponse(u.createdBy().id(), u.createdBy().name(), u.createdBy().userName());

    return new AdminUserResponse(
        u.id(), u.name(), u.userName(), u.document(), u.status(),
        u.createdAt(), u.lastLoginAt(), u.blockedUntil(), u.passwordExpiresAt(), createdBy, nimbusDeskGroups);
  }

}
