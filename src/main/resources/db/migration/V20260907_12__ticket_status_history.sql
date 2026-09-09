-- Auditoria explícita de toda transição de status (com.nimbusdesk.tickets.model.
-- TicketStatusHistory) - isso NÃO existe no NimbusFlow (lá o rastro é implícito), construído do
-- zero aqui porque o PROJECT_SPEC.md pede auditoria explícita pro helpdesk de TI. Coluna
-- "occurred_at" (não "timestamp", palavra reservada do Postgres) mapeia Java field "changedAt".
-- status_anterior é nulo só pra abertura inicial (ABERTO), que não gera linha de histórico.
CREATE TABLE ticket_status_history (
  id              UUID        PRIMARY KEY,
  ticket_id       UUID        NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
  status_anterior VARCHAR(30),
  status_novo     VARCHAR(30) NOT NULL,
  usuario_id      VARCHAR(100) NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ticket_status_history_ticket_id_idx ON ticket_status_history (ticket_id);
