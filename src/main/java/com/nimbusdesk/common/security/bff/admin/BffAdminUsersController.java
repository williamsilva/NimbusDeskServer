package com.nimbusdesk.common.security.bff.admin;

import com.nimbussystems.commons.security.bff.admin.AdminUserResponse;

import com.nimbussystems.commons.security.bff.admin.AdminUserRequest;

import com.nimbussystems.commons.security.bff.admin.AdminUserMinimalResponse;

import com.nimbussystems.commons.security.bff.admin.AdminSearchRequest;

import com.nimbussystems.commons.security.bff.admin.AdminBulkIdsRequest;

import com.nimbusdesk.common.security.BffAccessTokenService;
import com.nimbusdesk.common.security.CheckSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Menu "Segurança" &gt; Usuários - administração (não self-service, ver BffAccountController pra
 * "Minha conta"). Só usuários vinculados a algum grupo do NimbusDesk (ver AdminUserService.list) -
 * usuários são globais no NimbusCore, mas o NimbusDeskWeb não deve expor o diretório completo
 * (outros apps Nimbus, ex.: Cardsync).
 *
 * <p>Sem exclusão física (o NimbusCore não tem esse endpoint - só ativar/desativar) e sem reset de
 * senha pelo admin (não existe no NimbusCore - o único mecanismo é reenviar convite, que gera um
 * novo token de definição de senha pro próprio usuário).
 */
@RestController
@RequiredArgsConstructor
public class BffAdminUsersController {

  private final AdminUserService service;
  private final BffAccessTokenService accessTokenService;
  private final AdminUserModelAssembler modelAssembler;
  private final PagedResourcesAssembler<AdminUserResponse> pagedResourcesAssembler;

  @GetMapping("/bff/v1/users")
  @CheckSecurity.User.CanConsult
  public List<AdminUserResponse> list(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    return service.list(accessTokenService.getValidAccessToken(auth, request, response));
  }

  @GetMapping("/bff/v1/users/{id}")
  @CheckSecurity.User.CanConsult
  public AdminUserResponse get(
      @PathVariable UUID id, Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    return service.get(accessTokenService.getValidAccessToken(auth, request, response), id);
  }

  /** Sem permissão específica - qualquer usuário autenticado do NimbusDesk pode ver este
   *  seletor leve (ver AdminUserService.options). */
  @GetMapping("/bff/v1/users/options")
  @CheckSecurity.Authenticated
  public List<AdminUserMinimalResponse> options() {
    return service.options();
  }

  /** Mesma lista de options() - usada pelo filtro "Cadastrado por" das telas de Usuários/Grupos. */
  @GetMapping("/bff/v1/users/options-filter")
  @CheckSecurity.Authenticated
  public List<AdminUserMinimalResponse> optionsFilter() {
    return service.options();
  }

  @PostMapping("/bff/v1/users/search")
  @CheckSecurity.User.CanConsult
  public PagedModel<AdminUserModel> search(
      @RequestBody AdminSearchRequest body,
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    Page<AdminUserResponse> page =
        service.search(accessTokenService.getValidAccessToken(auth, request, response), body);
    return pagedResourcesAssembler.toModel(page, modelAssembler);
  }

  @PostMapping("/bff/v1/users")
  @ResponseStatus(HttpStatus.CREATED)
  @CheckSecurity.User.CanCreate
  public AdminUserResponse create(
      @Valid @RequestBody AdminUserRequest body,
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    return service.create(accessTokenService.getValidAccessToken(auth, request, response), body);
  }

  @PutMapping("/bff/v1/users/{id}")
  @CheckSecurity.User.CanChange
  public AdminUserResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody AdminUserRequest body,
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    return service.update(accessTokenService.getValidAccessToken(auth, request, response), id, body);
  }

  @PostMapping("/bff/v1/users/{id}/activate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.User.CanActiveOrInactive
  public void activate(@PathVariable UUID id, Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    service.activate(accessTokenService.getValidAccessToken(auth, request, response), id);
  }

  @PostMapping("/bff/v1/users/{id}/deactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.User.CanActiveOrInactive
  public void deactivate(@PathVariable UUID id, Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    service.deactivate(accessTokenService.getValidAccessToken(auth, request, response), id);
  }

  @PostMapping("/bff/v1/users/{id}/resend-invite")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.User.CanResendInvite
  public void resendInvite(@PathVariable UUID id, Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    service.resendInvite(accessTokenService.getValidAccessToken(auth, request, response), id);
  }

  @PostMapping("/bff/v1/users/activate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.User.CanActiveOrInactive
  public void activateBulk(
      @Valid @RequestBody AdminBulkIdsRequest body,
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    service.activateBulk(accessTokenService.getValidAccessToken(auth, request, response), body.ids());
  }

  @PostMapping("/bff/v1/users/deactivate")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @CheckSecurity.User.CanActiveOrInactive
  public void deactivateBulk(
      @Valid @RequestBody AdminBulkIdsRequest body,
      Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    service.deactivateBulk(accessTokenService.getValidAccessToken(auth, request, response), body.ids());
  }
}
