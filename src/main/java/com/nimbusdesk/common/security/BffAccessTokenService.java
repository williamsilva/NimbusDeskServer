package com.nimbusdesk.common.security;

import com.nimbussystems.commons.security.NimbusSecurityProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;

/**
 * Mesmo padrão do BffAccessTokenService do CardSyncServer: resolve (renovando se preciso, via
 * refresh_token) o access_token OAuth2 já em cache na sessão do usuário - usado só pra encaminhar
 * como Bearer nas chamadas server-to-server pro NimbusCore (perfil, troca de senha). Nunca chega
 * ao browser. OAuth2AuthorizedClientManager é autoconfigurado pelo Spring Boot (já existem
 * ClientRegistrationRepository e OAuth2AuthorizedClientRepository, vindos do oauth2Login()).
 */
@Service
@RequiredArgsConstructor
public class BffAccessTokenService {

  private static final String REGISTRATION_ID = "nimbusdesk-bff";

  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final NimbusSecurityProperties props;

  public String getValidAccessToken(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    if (!(authentication instanceof OAuth2AuthenticationToken oauth2Auth)) {
      throw new IllegalStateException("Not authenticated via oauth2Login");
    }

    OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
        .principal(oauth2Auth)
        .attribute(HttpServletRequest.class.getName(), request)
        .attribute(HttpServletResponse.class.getName(), response)
        .build();

    try {
      OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);
      if (client == null || client.getAccessToken() == null) {
        throw new IllegalStateException("No authorized client/access token available for " + REGISTRATION_ID);
      }
      return client.getAccessToken().getTokenValue();
    } catch (OAuth2AuthorizationException ex) {
      // Authorized client inválido mas a sessão HTTP ainda autenticada (típico depois de um
      // restart do backend) - sem isto, BffLoginController.login() só olha
      // token.isAuthenticated() (continua true) e manda o browser direto de volta pra SPA sem
      // refazer /oauth2/authorization/nimbusdesk-bff, causando um loop infinito de "renovar
      // sessão" (ApiExceptionHandler mapeia esta exceção pra 401 -> frontend chama /bff/login de
      // novo -> login() acha que já está logado -> volta pra SPA -> chamada falha de novo).
      // Invalidar a sessão aqui garante que o próximo /bff/login realmente refaça o fluxo OAuth2
      // do zero, mesmo princípio do revokeChainAndClearSession do CardSyncServer.
      clearSessionAndCookies(request, response);
      throw ex;
    }
  }

  private void clearSessionAndCookies(HttpServletRequest request, HttpServletResponse response) {
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    response.addHeader("Set-Cookie", clearCookieHeader("NIMBUSDESK_SESSION", true));
    response.addHeader("Set-Cookie", clearCookieHeader("NIMBUSDESK-XSRF-TOKEN", false));
  }

  private String clearCookieHeader(String name, boolean httpOnly) {
    StringBuilder header = new StringBuilder(name).append("=; Path=/; Max-Age=0");
    if (httpOnly) {
      header.append("; HttpOnly");
    }
    if (props.getCookies().isSecure()) {
      header.append("; Secure");
    }
    String domain = props.getCookies().getDomain();
    if (domain != null && !domain.isBlank()) {
      header.append("; Domain=").append(domain);
    }
    header.append("; SameSite=").append(props.getCookies().getSameSite());
    return header.toString();
  }
}
