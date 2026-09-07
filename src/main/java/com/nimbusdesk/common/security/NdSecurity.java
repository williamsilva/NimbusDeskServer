package com.nimbusdesk.common.security;

import com.nimbussystems.commons.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bean resolvido pelas expressões SpEL de {@link CheckSecurity} ({@code @ndSecurity.canXyz()}) —
 * cada método aqui é só um alias nomeado para {@link CurrentUserProvider#hasAuthority(String)},
 * mesma checagem (mesmo bypass de {@code ROLE_SUPPORT}) que já era feita imperativamente dentro
 * de cada Service via {@code requireAuthority(String)}. Nome do bean é "ndSecurity" (default do
 * Spring a partir do nome da classe) — não renomear sem atualizar as expressões em
 * {@link CheckSecurity}.
 *
 * <p>Nomes de permissão aqui batem exatamente com o contrato fechado seedado no NimbusAuth pro
 * app_key "nimbusdesk" (ver migration correspondente no repo NimbusAuth): SUPPORT, USERS_CONSULT,
 * USERS_CREATE, USERS_CHANGE, USERS_DELETE, USERS_ACTIVE_OR_INACTIVE, USERS_RESEND_INVITE,
 * GROUPS_CONSULT, GROUPS_CREATE, GROUPS_CHANGE, GROUPS_DELETE, GROUPS_ACTIVE_OR_INACTIVE,
 * GROUPS_MANAGEMENT_USER, GROUPS_MANAGEMENT_PERMISSION, EMAIL_SETTINGS_CONSULT,
 * EMAIL_SETTINGS_PROCESS, EMAIL_LOG_CONSULT, BACKUP_PROCESS. USERS_DELETE/GROUPS_ACTIVE_OR_INACTIVE
 * existem no contrato mas não têm endpoint correspondente ainda (sem exclusão física de usuário,
 * sem ativar/desativar grupo) - não precisam de método aqui.
 */
@Component
@RequiredArgsConstructor
public class NdSecurity {

  private final CurrentUserProvider currentUserProvider;

  /* Email Log */
  public boolean canConsultEmailLog() {
    return currentUserProvider.hasAuthority("PERM_EMAIL_LOG_CONSULT");
  }

  /* User (segurança > usuários) */
  public boolean canConsultUsers() {
    return currentUserProvider.hasAuthority("PERM_USERS_CONSULT");
  }

  public boolean canCreateUsers() {
    return currentUserProvider.hasAuthority("PERM_USERS_CREATE");
  }

  public boolean canChangeUsers() {
    return currentUserProvider.hasAuthority("PERM_USERS_CHANGE");
  }

  public boolean canActiveOrInactiveUsers() {
    return currentUserProvider.hasAuthority("PERM_USERS_ACTIVE_OR_INACTIVE");
  }

  public boolean canResendInviteUsers() {
    return currentUserProvider.hasAuthority("PERM_USERS_RESEND_INVITE");
  }

  /* Group (segurança > grupos) */
  public boolean canConsultGroups() {
    return currentUserProvider.hasAuthority("PERM_GROUPS_CONSULT");
  }

  public boolean canCreateGroups() {
    return currentUserProvider.hasAuthority("PERM_GROUPS_CREATE");
  }

  public boolean canChangeGroups() {
    return currentUserProvider.hasAuthority("PERM_GROUPS_CHANGE");
  }

  public boolean canDeleteGroups() {
    return currentUserProvider.hasAuthority("PERM_GROUPS_DELETE");
  }

  public boolean canManagePermissionGroups() {
    return currentUserProvider.hasAuthority("PERM_GROUPS_MANAGEMENT_PERMISSION");
  }

  public boolean canManageUserGroups() {
    return currentUserProvider.hasAuthority("PERM_GROUPS_MANAGEMENT_USER");
  }
}
