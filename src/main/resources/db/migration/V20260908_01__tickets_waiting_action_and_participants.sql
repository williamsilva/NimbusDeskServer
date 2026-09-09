-- Escopos novos de "Chamados" (com.nimbusdesk.tickets) além de "Meus chamados"/"Todos":
-- "Aguardando minha ação" (aguardando_acao_de) e "Compartilhados comigo" (ticket_participants).
-- Ver PROJECT_SPEC.md e TicketService#search.

ALTER TABLE tickets ADD COLUMN aguardando_acao_de VARCHAR(100);
CREATE INDEX tickets_aguardando_acao_de_idx ON tickets (aguardando_acao_de);

-- Participante: usuário adicionado por quem abriu o chamado (ou por CHAMADO_MANAGE) pra
-- acompanhar sem ser o solicitante nem o responsável - mesma técnica de "lista crua de raw ids"
-- já usada em com.nimbusdesk.tickets.model.Setor (userIds) e no Department do NimbusFlow, só que
-- numa tabela própria (não @ElementCollection) porque aqui também precisamos indexar por user_id
-- pra resolver a busca "Compartilhados comigo" com eficiência.
CREATE TABLE ticket_participants (
  id         UUID         PRIMARY KEY,
  ticket_id  UUID         NOT NULL REFERENCES tickets (id),
  user_id    VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uk_ticket_participants_ticket_user UNIQUE (ticket_id, user_id)
);

CREATE INDEX ticket_participants_ticket_id_idx ON ticket_participants (ticket_id);
CREATE INDEX ticket_participants_user_id_idx ON ticket_participants (user_id);
