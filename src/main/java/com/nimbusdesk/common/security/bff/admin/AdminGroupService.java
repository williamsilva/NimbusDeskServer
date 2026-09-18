package com.nimbusdesk.common.security.bff.admin;

import com.nimbussystems.commons.security.bff.admin.AdminUserRequest;

import com.nimbussystems.commons.security.bff.admin.AdminUserMinimalResponse;

import com.nimbussystems.commons.security.bff.admin.AdminSearchRequest;

import com.nimbussystems.commons.security.bff.admin.AdminPermissionOptionResponse;

import com.nimbussystems.commons.security.bff.admin.AdminGroupUsersRequest;

import com.nimbussystems.commons.security.bff.admin.AdminGroupSummaryResponse;

import com.nimbussystems.commons.security.bff.admin.AdminGroupResponse;

import com.nimbussystems.commons.security.bff.admin.AdminGroupRequest;

import com.nimbussystems.commons.security.bff.admin.AdminGroupPermissionsRequest;

import com.nimbussystems.commons.security.bff.admin.AdminGroupOptionResponse;

import com.nimbussystems.commons.security.bff.admin.AdminFilterSupport;

import com.nimbusdesk.common.security.NimbusCoreAdminClient;
import com.nimbusdesk.common.security.NimbusCoreAdminClient.RawGroup;
import com.nimbusdesk.common.security.NimbusCoreAdminClient.RawGroupInput;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Administração de grupos (menu Segurança) - só grupos/permissões do NimbusDesk
 * (NimbusCoreAdminClient já escopa por appKey=nimbusdesk nas leituras). Gerencia membros do grupo
 * por aqui também (GROUPS_MANAGEMENT_USER/PUT .../users), além do caminho já existente pela tela
 * de Usuários (AdminUserRequest.groupIds) - os dois convergem pro mesmo endpoint no NimbusCore.
 */
@Service
@RequiredArgsConstructor
public class AdminGroupService {

  private final NimbusCoreAdminClient client;

  /** Listagem completa (com contadores/data/autor, ver AdminGroupSummaryResponse) - usa
   *  /groups/search em vez de /groups/options porque este último não traz esses campos. */
  public List<AdminGroupSummaryResponse> list(String accessToken) {
    return client.searchAllGroups(accessToken).stream().map(this::toSummary).toList();
  }

  public AdminGroupResponse get(String accessToken, UUID id) {
    return toResponse(client.getGroup(accessToken, id));
  }

  public AdminGroupResponse create(String accessToken, AdminGroupRequest request) {
    return toResponse(client.createGroup(accessToken, new RawGroupInput(request.name(), request.description())));
  }

  public AdminGroupResponse update(String accessToken, UUID id, AdminGroupRequest request) {
    return toResponse(client.updateGroup(accessToken, id, new RawGroupInput(request.name(), request.description())));
  }

  public void delete(String accessToken, UUID id) {
    client.deleteGroup(accessToken, id);
  }

  public AdminGroupResponse updatePermissions(String accessToken, UUID id, AdminGroupPermissionsRequest request) {
    return toResponse(client.updateGroupPermissions(accessToken, id, request.permissionIds()));
  }

  public AdminGroupResponse updateUsers(String accessToken, UUID id, AdminGroupUsersRequest request) {
    return toResponse(client.updateGroupUsers(accessToken, id, request.userIds()));
  }

  public List<AdminPermissionOptionResponse> listPermissionOptions(String accessToken) {
    return client.listPermissionOptions(accessToken).stream()
        .map(p -> new AdminPermissionOptionResponse(p.id(), p.name(), p.description()))
        .toList();
  }

  public List<AdminGroupOptionResponse> options(String accessToken) {
    return client.listGroupOptions(accessToken).stream()
        .map(g -> new AdminGroupOptionResponse(g.id(), g.name(), g.description()))
        .toList();
  }

  /** Sem paginação/filtro real no NimbusCore pra esse recorte - ver
   *  {@link com.nimbusdesk.common.security.bff.admin.AdminUserService#search} para o padrão
   *  (mesmo motivo de embrulhar numa {@link PageImpl}). */
  public Page<AdminGroupSummaryResponse> search(String accessToken, AdminSearchRequest request) {
    List<AdminGroupSummaryResponse> all =
        client.searchAllGroups(accessToken).stream().map(this::toSummary).toList();

    List<AdminGroupSummaryResponse> sorted = sortGroups(filterGroups(all, request), request);

    int page = request.page() == null ? 0 : Math.max(0, request.page());
    int size = request.size() == null || request.size() <= 0 ? 20 : request.size();
    int from = Math.min(page * size, sorted.size());
    int to = Math.min(from + size, sorted.size());

    Pageable pageable = PageRequest.of(page, size);
    return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
  }

  private List<AdminGroupSummaryResponse> filterGroups(
      List<AdminGroupSummaryResponse> items, AdminSearchRequest request) {
    Map<String, Object> tableFilters = request.tableFilters();
    Map<String, Object> advanced = request.advanced();

    String name = AdminFilterSupport.textFilter(tableFilters, advanced, "name", "name");
    String description = AdminFilterSupport.textFilter(tableFilters, advanced, "description", "description");
    List<String> createdByIds = AdminFilterSupport.listFilter(tableFilters, advanced, "createdBy", "createdBy");
    Instant[] createdAtRange = AdminFilterSupport.periodInstantRange(
        tableFilters, advanced, "createdAt", "periodCreatedAt", "createdAt");
    String global = request.globalFilter();

    return items.stream()
        .filter(g -> AdminFilterSupport.containsIgnoreCase(g.name(), name))
        .filter(g -> AdminFilterSupport.containsIgnoreCase(g.description(), description))
        .filter(g -> createdByIds.isEmpty()
            || (g.createdBy() != null && createdByIds.contains(String.valueOf(g.createdBy().id()))))
        .filter(g -> AdminFilterSupport.withinRange(g.createdAt(), createdAtRange))
        .filter(g -> global == null || global.isBlank()
            || AdminFilterSupport.containsIgnoreCase(g.name(), global)
            || AdminFilterSupport.containsIgnoreCase(g.description(), global))
        .toList();
  }

  private List<AdminGroupSummaryResponse> sortGroups(
      List<AdminGroupSummaryResponse> items, AdminSearchRequest request) {
    AdminSearchRequest.SortItem sortItem =
        (request.sort() == null || request.sort().isEmpty()) ? null : request.sort().get(0);
    String field = sortItem != null && sortItem.field() != null ? sortItem.field() : "name";
    boolean desc = sortItem != null && sortItem.order() != null && sortItem.order() < 0;

    Comparator<AdminGroupSummaryResponse> comparator = switch (field) {
      case "description" -> Comparator.comparing(g -> orEmpty(g.description()), String.CASE_INSENSITIVE_ORDER);
      case "usersCount" -> Comparator.comparing(g -> g.usersCount() == null ? 0 : g.usersCount());
      case "permissionsCount" -> Comparator.comparing(g -> g.permissionsCount() == null ? 0 : g.permissionsCount());
      case "createdAt" -> Comparator.comparing(g -> orEpoch(g.createdAt()));
      case "createdBy" -> Comparator.comparing(
          g -> g.createdBy() == null ? "" : orEmpty(g.createdBy().name()), String.CASE_INSENSITIVE_ORDER);
      default -> Comparator.comparing(g -> orEmpty(g.name()), String.CASE_INSENSITIVE_ORDER);
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

  private AdminGroupSummaryResponse toSummary(RawGroup g) {
    AdminUserMinimalResponse createdBy = g.createdBy() == null
        ? null
        : new AdminUserMinimalResponse(g.createdBy().id(), g.createdBy().name(), g.createdBy().userName());

    return new AdminGroupSummaryResponse(
        g.id(), g.name(), g.description(), g.usersCount(), g.permissionsCount(), g.createdAt(), createdBy);
  }

  private AdminGroupResponse toResponse(RawGroup g) {
    List<AdminPermissionOptionResponse> permissions = g.permissions() == null
        ? List.of()
        : g.permissions().stream()
            .map(p -> new AdminPermissionOptionResponse(p.id(), p.name(), p.description()))
            .toList();

    List<AdminUserMinimalResponse> users = g.users() == null
        ? List.of()
        : g.users().stream()
            .map(u -> new AdminUserMinimalResponse(u.id(), u.name(), u.userName()))
            .toList();

    return new AdminGroupResponse(
        g.id(), g.name(), g.description(), g.usersCount(), g.permissionsCount(), g.createdAt(), permissions, users);
  }

}
