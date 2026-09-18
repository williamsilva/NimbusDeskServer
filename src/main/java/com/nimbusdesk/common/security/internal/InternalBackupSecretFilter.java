package com.nimbusdesk.common.security.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protege /internal/backup/** (chamado pelo NimbusCore pra puxar o backup deste servidor) -
 *  reaproveita o MESMO secret já configurado em NimbusCoreProxyProperties
 *  (nimbus.nimbusauth.internal-api-secret / NIMBUS_INTERNAL_API_SECRET), usado hoje só pra
 *  CHAMAR o NimbusCore (ver NimbusCoreInternalClient) - valor já idêntico em todos os apps
 *  Nimbus no Railway, nenhuma env var nova. Mesmo padrão de
 *  com.nimbusflow.common.security.internal.InternalBackupSecretFilter (portado byte-a-byte). */
public class InternalBackupSecretFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Internal-Secret";

  private final String expectedSecret;

  public InternalBackupSecretFilter(String expectedSecret) {
    this.expectedSecret = expectedSecret;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String provided = request.getHeader(HEADER);

    if (expectedSecret == null || expectedSecret.isBlank() || provided == null || !secretsMatch(expectedSecret, provided)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    filterChain.doFilter(request, response);
  }

  // Comparação constant-time — evita timing attack pra descobrir o secret byte a byte.
  private boolean secretsMatch(String expected, String provided) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
  }
}
