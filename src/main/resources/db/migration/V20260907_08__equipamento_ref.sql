-- Cópia local, somente leitura pelo app (com.nimbusdesk.tickets.model.EquipamentoRef) - escrita só
-- pelo job de sincronização (com.nimbusdesk.tickets.core.EquipamentoSyncService). id é o MESMO UUID
-- do Equipamento no NimbusFlowServer (chave de correlação, atribuído pelo job - sem
-- default/generated aqui).
CREATE TABLE equipamento_ref (
  id                 UUID         PRIMARY KEY,
  codigo             VARCHAR(60)  NOT NULL,
  nome               VARCHAR(200) NOT NULL,
  localizacao        VARCHAR(200),
  status_operacional VARCHAR(40),
  atualizado_em      TIMESTAMPTZ,
  sincronizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX equipamento_ref_codigo_idx ON equipamento_ref (codigo);
