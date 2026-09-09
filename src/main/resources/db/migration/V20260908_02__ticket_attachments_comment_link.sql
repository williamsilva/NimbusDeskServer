-- Anexo passa a poder estar vinculado a um comentário específico (melhora a visualização - o
-- anexo aparece junto da mensagem que o trouxe, na timeline, em vez de ficar solto numa lista
-- separada). Nullable: anexos enviados JUNTO com a abertura do chamado (POST /bff/v1/tickets)
-- continuam sem comment_id (não fazem parte de nenhuma resposta, são da descrição inicial).
ALTER TABLE ticket_attachments ADD COLUMN comment_id UUID REFERENCES ticket_comments (id);
CREATE INDEX ticket_attachments_comment_id_idx ON ticket_attachments (comment_id);
