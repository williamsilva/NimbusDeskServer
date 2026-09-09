package com.nimbusdesk.common.backup;

/** Alvos selecionáveis do backup sob demanda (tela Configurações &gt; Backup). Nomes fixados pelo
 *  frontend (BackupApiService/backup-settings.component.html no NimbusDeskWeb) - não renomear
 *  sem atualizar os dois lados. {@code FILES} adicionado em 2026-09-09 (pedido do usuário, "igual
 *  ao NimbusFlow") - compacta o bucket de anexos ("nimbusdesk-attachments"), ver S3VolumeZipper. */
public enum BackupTarget {
  NIMBUSDESK_DB,
  NIMBUSAUTH_DB,
  FILES
}
