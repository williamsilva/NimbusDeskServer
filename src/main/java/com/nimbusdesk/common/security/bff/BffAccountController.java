package com.nimbusdesk.common.security.bff;

import com.nimbussystems.commons.security.bff.ProfileResponse;

import com.nimbussystems.commons.security.bff.PasswordPolicyResponse;

import com.nimbussystems.commons.security.bff.PasswordPolicyCheckRequest;

import com.nimbussystems.commons.security.bff.ChangePasswordRequest;

import com.nimbusdesk.common.security.BffAccessTokenService;
import com.nimbussystems.commons.security.CurrentUserProvider;
import com.nimbussystems.commons.security.NimbusCoreClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Minha conta" (perfil próprio) e política/troca de senha - proxy tipado pro NimbusCore, mesmo
 * papel do BffAdminProxyController/PasswordPolicyProxyController do CardSyncServer. Sem
 * suposição de escopo além do que o próprio NimbusCore já garante (self-service: o usuário só
 * consegue ver/alterar os próprios dados, nunca de outro usuário - ver MeProfileController e
 * MePasswordChangeController no NimbusCore).
 */
@RestController
@RequiredArgsConstructor
public class BffAccountController {

  private final NimbusCoreClient nimbusCoreClient;
  private final BffAccessTokenService accessTokenService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping("/bff/v1/me/profile")
  public ProfileResponse getMyProfile(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    String accessToken = accessTokenService.getValidAccessToken(auth, request, response);
    return nimbusCoreClient.getMyProfile(accessToken);
  }

  @GetMapping("/bff/v1/password-policy")
  public PasswordPolicyResponse getPasswordPolicy() {
    return nimbusCoreClient.getPasswordPolicy();
  }

  @PostMapping("/bff/v1/password-policy/check")
  public PasswordPolicyResponse checkPasswordPolicy(@Valid @RequestBody PasswordPolicyCheckRequest request) {
    String username = currentUserProvider.getCurrentUser().username();
    return nimbusCoreClient.checkPasswordPolicy(request.password(), request.confirmPassword(), username);
  }

  @PutMapping("/bff/v1/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changeMyPassword(
      @Valid @RequestBody ChangePasswordRequest request,
      Authentication auth,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String accessToken = accessTokenService.getValidAccessToken(auth, httpRequest, httpResponse);
    nimbusCoreClient.changeMyPassword(accessToken, request);
  }
}
