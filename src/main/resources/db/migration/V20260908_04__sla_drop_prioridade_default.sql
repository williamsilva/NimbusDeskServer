-- 2026-09-09: remove a "prioridade padrão" do cadastro de SLA - desde a introdução do campo
-- "Prioridade" na abertura do chamado (o solicitante sempre informa explicitamente agora, ver
-- TicketRequest#prioridade), sla.prioridade_default nunca mais era usado (era só fallback pra
-- request.prioridade() nula, algo que não acontece mais - ver TicketService#create).
ALTER TABLE slas DROP COLUMN prioridade_default;
