package com.nimbusdesk.common.security.bff;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbussystems.commons.security.NimbusSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
@RequiredArgsConstructor
public class BffLogoutController {

  private static final Logger log = LoggerFactory.getLogger(BffLogoutController.class);
  private static final String REGISTRATION_ID = "nimbusdesk-bff";

  public record LogoutResponse(String logoutUrl) {
  }

  private record TokenRefreshResponse(@JsonProperty("id_token") String idToken) {
  }

  private final NimbusSecurityProperties props;
  private final OAuth2AuthorizedClientRepository authorizedClientRepository;
  private final RestClient.Builder restClientBuilder;

  @PostMapping("/bff/logout")
  public ResponseEntity<LogoutResponse> logout(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
    String logoutUrl = props.getWeb().getSpaBaseUrl();

    if (auth != null) {
      OAuth2AuthorizedClient authorizedClient =
          authorizedClientRepository.loadAuthorizedClient(REGISTRATION_ID, auth, request);

      // RP-Initiated Logout (OIDC): sem isso, o NimbusAuth mantém sua própria sessão de login
      // válida e o próximo /oauth2/authorize reautentica via SSO silenciosamente.
      String idTokenHint = resolveIdTokenHint(auth, authorizedClient);
      if (idTokenHint != null) {
        logoutUrl = props.getIssuer() + "/connect/logout"
            + "?id_token_hint=" + URLEncoder.encode(idTokenHint, StandardCharsets.UTF_8)
            + "&post_logout_redirect_uri=" + URLEncoder.encode(props.getWeb().getSpaBaseUrl(), StandardCharsets.UTF_8);
      }

      authorizedClientRepository.removeAuthorizedClient(REGISTRATION_ID, auth, request, response);
    }

    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }

    response.addHeader("Set-Cookie", clearCookieHeader("NIMBUSDESK_SESSION", true));
    response.addHeader("Set-Cookie", clearCookieHeader("NIMBUSDESK-XSRF-TOKEN", false));

    return ResponseEntity.ok(new LogoutResponse(logoutUrl));
  }

  /**
   * O id_token guardado na sessão (OidcUser, fixado no login original) fica órfão rápido: cada
   * refresh silencioso do access_token (automático, a cada poucos minutos - ver access-token-ttl
   * no NimbusAuth) SUBSTITUI o id_token daquela authorization no NimbusAuth, e o /connect/logout
   * rejeita com invalid_token qualquer id_token_hint que não bata com o que está lá agora (ver
   * OidcLogoutAuthenticationProvider) - sem um id_token válido, a revogação de tokens no logout
   * (RevokeTokensLogoutHandler, do lado do NimbusAuth) nunca chega a rodar.
   *
   * <p>Por isso pedimos aqui um id_token fresco via grant refresh_token direto contra o token
   * endpoint - fora do OAuth2AuthorizedClientManager padrão, que não expõe id_token (só
   * access/refresh token, id_token é conceito só do login OIDC). Se isso falhar por qualquer
   * motivo (rede, refresh_token já invalidado, etc.), cai pro id_token da sessão mesmo estando
   * possivelmente órfão - pior caso, o RP-Initiated Logout falha como já falhava antes desse
   * ajuste, mas o logout local (sessão do BFF) continua funcionando normalmente.
   */
  private String resolveIdTokenHint(Authentication auth, OAuth2AuthorizedClient authorizedClient) {
    if (authorizedClient != null && authorizedClient.getRefreshToken() != null) {
      String freshIdToken = fetchFreshIdToken(authorizedClient);
      if (freshIdToken != null) {
        return freshIdToken;
      }
    }

    return legacyIdToken(auth);
  }

  private String fetchFreshIdToken(OAuth2AuthorizedClient authorizedClient) {
    ClientRegistration registration = authorizedClient.getClientRegistration();

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "refresh_token");
    body.add("refresh_token", authorizedClient.getRefreshToken().getTokenValue());
    body.add("scope", "openid");

    try {
      TokenRefreshResponse tokenResponse = restClientBuilder.build()
          .post()
          .uri(registration.getProviderDetails().getTokenUri())
          .headers(headers -> headers.setBasicAuth(registration.getClientId(), registration.getClientSecret()))
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(body)
          .retrieve()
          .body(TokenRefreshResponse.class);

      return tokenResponse != null ? tokenResponse.idToken() : null;
    } catch (RestClientException e) {
      log.warn("Não foi possível obter id_token fresco pra logout - RP-Initiated Logout pode falhar: {}",
          e.getMessage());
      return null;
    }
  }

  private String legacyIdToken(Authentication auth) {
    if (auth.getPrincipal() instanceof OidcUser oidc && oidc.getIdToken() != null) {
      return oidc.getIdToken().getTokenValue();
    }
    return null;
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
