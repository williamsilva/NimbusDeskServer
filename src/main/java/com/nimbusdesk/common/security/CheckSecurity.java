package com.nimbusdesk.common.security;

import com.nimbussystems.commons.security.CurrentUserProvider;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Checagem de permissão declarativa nos endpoints, mesmo padrão do CardSync ({@code
 * com.cardsync.core.security.CheckSecurity}) — cada anotação delega, via SpEL, para um método de
 * {@link NdSecurity} nomeado pela ação ({@code @ndSecurity.canConsultUsers()}), que por sua vez
 * reaproveita {@link CurrentUserProvider#hasAuthority(String)} (mesma checagem que já existia
 * imperativamente dentro dos Services, incluindo o bypass de {@code ROLE_SUPPORT}) — não duplica
 * lógica de autorização nova, só move onde ela é aplicada: do corpo do Service (imperativo) para a
 * assinatura do Controller (declarativo). Requer {@code @EnableMethodSecurity} em
 * {@link SecurityConfig} para ter efeito.
 *
 * <p>Base mínima do NimbusDesk (2026-09-07): só os módulos Usuários/Grupos (BFF admin do
 * NimbusAuth) e Email Log ficam aqui. Configuração de E-mail (BffEmailSettingsController) e Backup
 * (BffBackupController) fazem sua própria checagem imperativa via {@link CurrentUserProvider}
 * direto (mesmo padrão de origem, sem passar por esta anotação) - ver os respectivos pacotes.
 */
public @interface CheckSecurity {

  @Target(METHOD)
  @Retention(RUNTIME)
  @PreAuthorize("isAuthenticated()")
  @interface Authenticated {
  }

  @interface EmailLog {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultEmailLog()")
    @interface CanConsult {
    }
  }

  @interface User {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultUsers()")
    @interface CanConsult {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canCreateUsers()")
    @interface CanCreate {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canChangeUsers()")
    @interface CanChange {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canActiveOrInactiveUsers()")
    @interface CanActiveOrInactive {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canResendInviteUsers()")
    @interface CanResendInvite {
    }
  }

  @interface Group {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultGroups()")
    @interface CanConsult {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canCreateGroups()")
    @interface CanCreate {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canChangeGroups()")
    @interface CanChange {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canDeleteGroups()")
    @interface CanDelete {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canManagePermissionGroups()")
    @interface CanManagePermission {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canManageUserGroups()")
    @interface CanManageUser {
    }
  }

  /** CHAMADO_CONSULT/CHAMADO_MANAGE - usadas aqui só onde a checagem NÃO depende do chamado
   *  específico carregado (ex.: dashboard). A visibilidade de {@code GET /{id}}/edição/comentário
   *  ("meu chamado" OU CHAMADO_CONSULT/MANAGE) é checada imperativamente dentro de
   *  com.nimbusdesk.tickets.core.TicketService via NdSecurity, não por esta anotação. */
  @interface Ticket {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultChamados()")
    @interface CanConsult {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canManageChamados()")
    @interface CanManage {
    }
  }

  @interface Category {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultCategorias()")
    @interface CanConsult {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canManageCategorias()")
    @interface CanManage {
    }
  }

  @interface Sla {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultSlas()")
    @interface CanConsult {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canManageSlas()")
    @interface CanManage {
    }
  }

  @interface Setor {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultSetores()")
    @interface CanConsult {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canManageSetores()")
    @interface CanManage {
    }
  }

  @interface EquipamentoRef {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultEquipamentoRefs()")
    @interface CanConsult {
    }
  }

  /** Configurações &gt; Automação de chamados (2026-09-09) - periodicidade dos 3 jobs (alerta de
   *  sem responsável, alerta de pendência de resposta, auto-fechamento), ver
   *  com.nimbusdesk.tickets.model.TicketAutomationSettings. */
  @interface TicketAutomation {
    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canConsultTicketAutomation()")
    @interface CanConsult {
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @PreAuthorize("@ndSecurity.canManageTicketAutomation()")
    @interface CanManage {
    }
  }
}
