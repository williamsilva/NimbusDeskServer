-- Configuração dos 3 jobs de automação de chamados pedidos pelo usuário em 2026-09-09 (ver
-- com.nimbusdesk.tickets.model.TicketAutomationSettings): alerta de chamados ABERTO sem
-- responsável, alerta de pendência de resposta (solicitante/compartilhados ou responsável), e
-- auto-fechamento (RESOLVIDO -> FECHADO). Linha única, mesmo padrão de slas/email_settings -
-- TicketAutomationSettingsRepository.findFirstBy() sempre lê/atualiza a mesma linha, nunca há mais
-- de uma. Sem seed - antes do primeiro update() pela tela, TicketAutomationSettingsService cai pros
-- defaults em código (1/1/1/5 dias).
CREATE TABLE ticket_automation_settings (
  id                                          UUID        PRIMARY KEY,
  unassigned_alert_periodicidade_dias         INT         NOT NULL,
  pending_response_alert_periodicidade_dias   INT         NOT NULL,
  auto_close_periodicidade_dias               INT         NOT NULL,
  auto_close_carencia_dias                    INT         NOT NULL,
  created_at                                  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
