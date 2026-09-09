-- Comentário do chamado (com.nimbusdesk.tickets.model.TicketComment). interno=true só é visível
-- pra quem tem CHAMADO_MANAGE (ver TicketCommentService).
CREATE TABLE ticket_comments (
  id         UUID          PRIMARY KEY,
  ticket_id  UUID          NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
  autor_id   VARCHAR(100)  NOT NULL,
  mensagem   VARCHAR(4000) NOT NULL,
  interno    BOOLEAN       NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ticket_comments_ticket_id_idx ON ticket_comments (ticket_id);
