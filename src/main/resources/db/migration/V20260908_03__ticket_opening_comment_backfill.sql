-- 2026-09-08: a descrição informada na abertura do chamado passa a existir como o PRIMEIRO
-- comentário da conversa (autor=solicitante, público) em vez de um bloco de texto separado da
-- timeline (ver TicketService#create/TicketCommentService#createOpeningComment) - unifica de vez o
-- padrão "anexo sempre vinculado a um comentário", sem exceção nenhuma pra abertura (ver
-- TicketAttachmentService).
--
-- Backfill pros chamados já existentes: cria o comentário de abertura de cada um (created_at =
-- data_abertura do chamado, pra manter a ordem cronológica correta na timeline) e reaponta os
-- anexos que ainda estavam soltos (comment_id IS NULL - só podiam ser da abertura, anexo de
-- comentário sempre teve comment_id preenchido desde a V20260908_02) pro comentário recém-criado.
--
-- CTE com RETURNING (em vez de 2 statements separados) - garante que o UPDATE só aponte pro
-- comentário que ESTE backfill acabou de criar, mesmo que o chamado já tenha outros comentários
-- (respostas reais) com o mesmo ticket_id - um JOIN direto contra ticket_comments seria ambíguo
-- nesse caso.
WITH inserted AS (
  INSERT INTO ticket_comments (id, ticket_id, autor_id, mensagem, interno, created_at)
  SELECT gen_random_uuid(), t.id, t.solicitante_id, t.descricao, false, t.data_abertura
  FROM tickets t
  RETURNING id, ticket_id
)
UPDATE ticket_attachments a
SET comment_id = i.id
FROM inserted i
WHERE a.comment_id IS NULL
  AND i.ticket_id = a.ticket_id;
