package com.nimbusdesk.common.security;

import com.nimbussystems.commons.security.NimbusSecurityProperties;

import com.nimbussystems.commons.security.NimbusAuthProxyProperties;

import com.nimbussystems.commons.security.ResourceServerJwtBeans;

import com.nimbussystems.commons.security.SpaCsrfTokenRequestHandler;

import com.nimbussystems.commons.security.OAuth2ClientHttpConfig;

import com.nimbussystems.commons.security.CsrfCookieFilter;

import com.nimbusdesk.common.security.internal.InternalBackupSecretFilter;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Padrão BFF (igual ao Cardsync, ver ADR em PROJECT_SPEC.md seção 2): duas security filter
 * chains independentes — API stateless (JWT emitido pelo NimbusAuth) e BFF stateful (sessão +
 * cookies + oauth2Login contra o NimbusAuth). Sem a camada extra de hardening (CSP/HSTS/
 * correlation-id) que o Cardsync tem — fora de escopo aqui, ver checklist da Fase 1.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  private final NimbusSecurityProperties props;

  // ---------------------------
  // 0) INTERNAL BACKUP CHAIN (/internal/backup/**) - machine-to-machine, chamada pelo NimbusAuth
  // pra puxar o backup deste servidor (banco + arquivos), autenticado por secret compartilhado
  // (ver InternalBackupSecretFilter), não por sessão/JWT. Reaproveita o MESMO secret já
  // configurado em NimbusAuthProxyProperties (NIMBUS_INTERNAL_API_SECRET, usado hoje só pra
  // CHAMAR o NimbusAuth/NimbusFlow) - nenhuma env var nova. @Order menor que apiChain (10) e
  // bffChain (20) pra ser avaliada primeiro - o securityMatcher restrito a /internal/backup/**
  // garante que ela nunca interfere nas outras duas.
  // ---------------------------
  @Bean
  @Order(5)
  public SecurityFilterChain internalBackupChain(
      HttpSecurity http, NimbusAuthProxyProperties nimbusAuthProxyProperties) throws Exception {

    http.securityMatcher("/internal/backup/**");
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.csrf(AbstractHttpConfigurer::disable);
    http.cors(AbstractHttpConfigurer::disable);
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    http.addFilterBefore(
        new InternalBackupSecretFilter(nimbusAuthProxyProperties.getInternalApiSecret()),
        HeaderWriterFilter.class);
    return http.build();
  }

  // ---------------------------
  // 0.1) INTERNAL EMAIL SETTINGS CHAIN (/internal/email-settings/**) - machine-to-machine, chamada
  // pelo NimbusAuth pra centralizar a tela "E-mail dos Apps". Mesmo padrão exato da chain de
  // backup acima (mesmo secret compartilhado, mesmo filtro InternalBackupSecretFilter).
  // ---------------------------
  @Bean
  @Order(6)
  public SecurityFilterChain internalEmailSettingsChain(
      HttpSecurity http, NimbusAuthProxyProperties nimbusAuthProxyProperties) throws Exception {

    http.securityMatcher("/internal/email-settings/**");
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.csrf(AbstractHttpConfigurer::disable);
    http.cors(AbstractHttpConfigurer::disable);
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    http.addFilterBefore(
        new InternalBackupSecretFilter(nimbusAuthProxyProperties.getInternalApiSecret()),
        HeaderWriterFilter.class);
    return http.build();
  }

  // ---------------------------
  // 1) API CHAIN (/api/**) STATELESS
  // ---------------------------
  @Bean
  @Order(10)
  public SecurityFilterChain apiChain(
      HttpSecurity http,
      JwtDecoder jwtDecoder,
      OAuth2TokenValidator<Jwt> jwtTokenValidator,
      ResourceServerJwtBeans.JwtAuthenticationConverterAdapter jwtAuthConverter) throws Exception {

    http.securityMatcher("/api/**");
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.csrf(AbstractHttpConfigurer::disable);
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

    http.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
        .anyRequest().authenticated());

    if (props.getResourceServer().isEnabled()) {
      if (jwtDecoder instanceof NimbusJwtDecoder nimbus) {
        nimbus.setJwtValidator(jwtTokenValidator);
      }
      http.oauth2ResourceServer(oauth2 -> oauth2
          .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthConverter)));
    }

    return http.build();
  }

  // ---------------------------
  // 2) BFF CHAIN (/bff/**) STATEFUL
  // ---------------------------
  @Bean
  @Order(20)
  public SecurityFilterChain bffChain(
      HttpSecurity http,
      OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver) throws Exception {

    http.securityMatcher("/bff/**", "/oauth2/authorization/**", "/login/oauth2/**");

    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

    http.csrf(csrf -> csrf
        .csrfTokenRepository(csrfTokenRepository())
        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));
    http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

    http.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.GET, "/bff/login", "/bff/csrf").permitAll()
        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/**").permitAll()
        .anyRequest().authenticated());

    http.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

    http.oauth2Login(o -> o
        .authorizationEndpoint(a -> a.authorizationRequestResolver(pkceAuthorizationRequestResolver))
        .userInfoEndpoint(u -> u.oidcUserService(bffOidcUserService()))
        .successHandler(oauth2SpaSuccessHandler())
        .failureHandler(oauth2LoginFailureHandler()));

    http.logout(AbstractHttpConfigurer::disable);
    http.formLogin(AbstractHttpConfigurer::disable);

    return http.build();
  }

  /**
   * PKCE no fluxo authorization_code: mesmo sendo um client confidential (client_secret_basic),
   * o code_verifier ainda protege contra um authorization code interceptado em trânsito.
   * Combina com requireProofKey(true) no RegisteredClient do lado do NimbusAuth.
   */
  @Bean
  public OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {
    var resolver = new DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    return resolver;
  }

  private SimpleUrlAuthenticationSuccessHandler oauth2SpaSuccessHandler() {
    SimpleUrlAuthenticationSuccessHandler handler =
        new SimpleUrlAuthenticationSuccessHandler(props.getWeb().getSpaBaseUrl());
    handler.setAlwaysUseDefaultTargetUrl(true);
    return handler;
  }

  /**
   * Default do Spring Security aqui seria redirect silencioso pra /login?error (sem log, sem
   * handler pra essa rota - vira 404 confuso) - loga a causa real e manda de volta pra SPA.
   */
  private AuthenticationFailureHandler oauth2LoginFailureHandler() {
    SimpleUrlAuthenticationFailureHandler delegate =
        new SimpleUrlAuthenticationFailureHandler(props.getWeb().getSpaBaseUrl());
    return (request, response, exception) -> {
      log.error("OAuth2 login failed", exception);
      delegate.onAuthenticationFailure(request, response, exception);
    };
  }

  /**
   * groups/permissions são claims de autorização - só vêm no access_token (padrão OIDC), então
   * lemos do /userinfo (chamado pelo OidcUserService com o access_token como bearer), não do
   * id_token (que carrega só identidade).
   */
  private OAuth2UserService<OidcUserRequest, OidcUser> bffOidcUserService() {
    var delegate = new OidcUserService();
    delegate.setOauth2UserService(userInfoOauth2UserService());

    return request -> {
      var oidc = delegate.loadUser(request);
      var authorities = new LinkedHashSet<GrantedAuthority>(oidc.getAuthorities());
      var userInfo = oidc.getUserInfo();

      var groups = userInfo != null ? userInfo.getClaimAsStringList("groups") : null;
      if (groups != null) {
        groups.forEach(g -> authorities.add(new SimpleGrantedAuthority("ROLE_" + g)));
      }

      var permissions = userInfo != null ? userInfo.getClaimAsStringList("permissions") : null;
      if (permissions != null) {
        permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p)));
      }

      return new DefaultOidcUser(authorities, oidc.getIdToken(), oidc.getUserInfo(), "username");
    };
  }

  /**
   * O OidcUserService acima delega no /userinfo (Bearer access_token) pra este
   * DefaultOAuth2UserService interno, que por padrão usa seu próprio {@code RestOperations} (um
   * {@code RestTemplate} sem timeout) - um terceiro cliente HTTP separado dos dois já corrigidos
   * (chamadas gerais via RestClientTimeoutConfig, renovação de refresh_token via
   * OAuth2ClientHttpConfig). Sem isto, uma lentidão do NimbusAuth durante o /userinfo do login
   * trava a autenticação inteira até o gateway derrubar a conexão (504) - só reproduzido em contas
   * que precisam refazer o login completo (sessão nova ou invalidada, ver
   * BffAccessTokenService#clearSessionAndCookies), já que uma sessão com authorized client ainda
   * válido nunca reexecuta este passo.
   */
  private DefaultOAuth2UserService userInfoOauth2UserService() {
    DefaultOAuth2UserService service = new DefaultOAuth2UserService();
    service.setRestOperations(new RestTemplate(httpTimeouts()));
    return service;
  }

  private JdkClientHttpRequestFactory httpTimeouts() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(10));
    return factory;
  }

  private CookieCsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    // Nome distinto do padrão "XSRF-TOKEN" - mesma colisão de cookie entre apps Nimbus no
    // domínio "localhost" explicada em application.yml (server.servlet.session.cookie.name).
    repository.setCookieName("NIMBUSDESK-XSRF-TOKEN");
    repository.setHeaderName("X-XSRF-TOKEN");
    repository.setCookiePath("/");
    repository.setCookieCustomizer(cookie -> {
      String domain = props.getCookies().getDomain();
      if (domain != null && !domain.isBlank()) {
        cookie.domain(domain);
      }
      cookie.secure(props.getCookies().isSecure());
      cookie.sameSite(props.getCookies().getSameSite());
    });
    return repository;
  }

  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(props.getWeb().getAllowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Requested-With"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
