-- Anexo do chamado (com.nimbusdesk.tickets.model.TicketAttachment) - storage_key é a key do objeto
-- no bucket MinIO (ver com.nimbusdesk.common.storage.StorageService), nunca a URL (assinada sob
-- demanda, TTL curto).
CREATE TABLE ticket_attachments (
  id            UUID         PRIMARY KEY,
  ticket_id     UUID         NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
  nome_arquivo  VARCHAR(300) NOT NULL,
  storage_key   VARCHAR(500) NOT NULL,
  tipo_mime     VARCHAR(100),
  tamanho_bytes BIGINT,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ticket_attachments_ticket_id_idx ON ticket_attachments (ticket_id);
