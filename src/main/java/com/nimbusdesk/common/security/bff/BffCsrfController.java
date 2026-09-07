package com.nimbusdesk.common.security.bff;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Só pra garantir que o cookie NIMBUSDESK-XSRF-TOKEN seja emitido (Double Submit) - o
 * CsrfCookieFilter já força isso ao "tocar" no token em toda requisição do chain BFF, mas sem
 * NENHUM endpoint mapeado em GET /bff/csrf, a SPA (CsrfService.ensureCsrfCookie(), chamada antes
 * de login/logout e antes da primeira escrita da sessão) recebia 404 - o filtro roda antes do
 * dispatch e ainda seta o cookie como efeito colateral, então o fluxo funcionava mesmo assim, só
 * com um toast de erro feio na tela. Mesmo padrão de BffCsrfController no CardsyncServer
 * (esquecido na migração original pra este projeto).
 */
@RestController
public class BffCsrfController {

  @GetMapping("/bff/csrf")
  public ResponseEntity<Void> csrf() {
    return ResponseEntity.noContent().build();
  }
}
