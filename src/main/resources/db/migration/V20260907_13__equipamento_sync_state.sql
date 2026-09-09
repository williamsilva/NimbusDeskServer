-- Linha única (com.nimbusdesk.tickets.model.EquipamentoSyncState) - guarda o estado da última
-- tentativa de com.nimbusdesk.tickets.core.EquipamentoSyncService. CHECK (id = 1) garante que só
-- exista essa linha (mesmo truque de "singleton row" já usado em email_settings, mas com id
-- fixo em vez de UUID - aqui não há necessidade de um id "de verdade", é só uma trava).
CREATE TABLE equipamento_sync_state (
  id                      SMALLINT PRIMARY KEY DEFAULT 1,
  last_success_started_at TIMESTAMPTZ,
  last_attempt_at         TIMESTAMPTZ,
  last_error              VARCHAR(1000),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT equipamento_sync_state_single_row CHECK (id = 1)
);
