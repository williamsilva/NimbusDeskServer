package com.nimbusdesk.common.security.bff;

import com.nimbussystems.commons.security.bff.ProfileResponse;

import com.nimbussystems.commons.security.bff.PasswordPolicyResponse;

import com.nimbussystems.commons.security.bff.PasswordPolicyCheckRequest;

import com.nimbussystems.commons.security.bff.ChangePasswordRequest;

import com.nimbusdesk.common.security.BffAccessTokenService;
import com.nimbussystems.commons.security.CurrentUserProvider;
import com.nimbussystems.commons.security.NimbusAuthClient;
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
 * "Minha conta" (perfil próprio) e política/troca de senha - proxy tipado pro NimbusAuth, mesmo
 * papel do BffAdminProxyController/PasswordPolicyProxyController do CardSyncServer. Sem
 * suposição de escopo além do que o próprio NimbusAuth já garante (self-service: o usuário só
 * consegue ver/alterar os próprios dados, nunca de outro usuário - ver MeProfileController e
 * MePasswordChangeController no NimbusAuth).
 */
@RestController
@RequiredArgsConstructor
public class BffAccountController {

  private final NimbusAuthClient nimbusAuthClient;
  private final BffAccessTokenService accessTokenService;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping("/bff/v1/me/profile")
  public ProfileResponse getMyProfile(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    String accessToken = accessTokenService.getValidAccessToken(auth, request, response);
    return nimbusAuthClient.getMyProfile(accessToken);
  }

  @GetMapping("/bff/v1/password-policy")
  public PasswordPolicyResponse getPasswordPolicy() {
    return nimbusAuthClient.getPasswordPolicy();
  }

  @PostMapping("/bff/v1/password-policy/check")
  public PasswordPolicyResponse checkPasswordPolicy(@Valid @RequestBody PasswordPolicyCheckRequest request) {
    String username = currentUserProvider.getCurrentUser().username();
    return nimbusAuthClient.checkPasswordPolicy(request.password(), request.confirmPassword(), username);
  }

  @PutMapping("/bff/v1/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changeMyPassword(
      @Valid @RequestBody ChangePasswordRequest request,
      Authentication auth,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String accessToken = accessTokenService.getValidAccessToken(auth, httpRequest, httpResponse);
    nimbusAuthClient.changeMyPassword(accessToken, request);
  }
}
