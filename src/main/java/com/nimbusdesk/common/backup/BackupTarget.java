package com.nimbusdesk.common.backup;

/** Alvos selecionáveis do backup sob demanda (tela Configurações &gt; Backup). Nomes fixados pelo
 *  frontend (BackupApiService/backup-settings.component.html no NimbusDeskWeb) - não renomear
 *  sem atualizar os dois lados. Sem FILES/S3 aqui - o NimbusDesk ainda não tem storage de
 *  arquivos (diferente do NimbusFlowServer, de onde este módulo foi portado). */
public enum BackupTarget {
  NIMBUSDESK_DB,
  NIMBUSAUTH_DB
}
