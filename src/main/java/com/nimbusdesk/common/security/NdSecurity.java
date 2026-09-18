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
 * <p>Nomes de permissão aqui batem exatamente com o contrato fechado seedado no NimbusCore pro
 * app_key "nimbusdesk" (ver migration correspondente no repo NimbusCore): SUPPORT, USERS_CONSULT,
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

  /* Módulo de negócio "Chamados de TI" (2026-09-07, migration
   * V20260907_03__nimbusdesk_ticket_permissions_seed.sql no repo NimbusCore). canConsultChamados/
   * canManageChamados também são chamados IMPERATIVAMENTE dentro de com.nimbusdesk.tickets.core
   * (não só via @CheckSecurity declarativo) - a visibilidade de um chamado específico depende de
   * dado carregado em runtime (dono/responsável, "meus chamados"), não expressável só na
   * assinatura do controller. */
  public boolean canConsultChamados() {
    return currentUserProvider.hasAuthority("PERM_CHAMADO_CONSULT");
  }

  public boolean canManageChamados() {
    return currentUserProvider.hasAuthority("PERM_CHAMADO_MANAGE");
  }

  public boolean canConsultCategorias() {
    return currentUserProvider.hasAuthority("PERM_CATEGORIA_CONSULT");
  }

  public boolean canManageCategorias() {
    return currentUserProvider.hasAuthority("PERM_CATEGORIA_MANAGE");
  }

  public boolean canConsultSlas() {
    return currentUserProvider.hasAuthority("PERM_SLA_CONSULT");
  }

  public boolean canManageSlas() {
    return currentUserProvider.hasAuthority("PERM_SLA_MANAGE");
  }

  public boolean canConsultSetores() {
    return currentUserProvider.hasAuthority("PERM_SETOR_TI_CONSULT");
  }

  public boolean canManageSetores() {
    return currentUserProvider.hasAuthority("PERM_SETOR_TI_MANAGE");
  }

  /** Sem canManageEquipamentoRefs - só leitura, escrito exclusivamente pelo job de sincronização
   *  (com.nimbusdesk.tickets.core.EquipamentoSyncService), nunca por um usuário via API. */
  public boolean canConsultEquipamentoRefs() {
    return currentUserProvider.hasAuthority("PERM_EQUIPAMENTO_REF_CONSULT");
  }

  /** Configurações &gt; Automação de chamados (2026-09-09, migration correspondente
   *  V20260909_01__nimbusdesk_ticket_automation_permissions_seed.sql no repo NimbusCore). */
  public boolean canConsultTicketAutomation() {
    return currentUserProvider.hasAuthority("PERM_TICKET_AUTOMATION_CONSULT");
  }

  public boolean canManageTicketAutomation() {
    return currentUserProvider.hasAuthority("PERM_TICKET_AUTOMATION_MANAGE");
  }
}
