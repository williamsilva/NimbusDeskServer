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
}
