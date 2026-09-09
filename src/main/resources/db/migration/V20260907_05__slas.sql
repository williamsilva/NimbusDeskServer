-- Configurações > SLAs (com.nimbusdesk.tickets.model.Sla). tempoResolucaoMin é usado pra calcular
-- Ticket.dataLimiteSla na abertura (dataAbertura + tempoResolucaoMin minutos), ver TicketService.
CREATE TABLE slas (
  id                  UUID         PRIMARY KEY,
  nome                VARCHAR(120) NOT NULL,
  tempo_resposta_min  INT          NOT NULL,
  tempo_resolucao_min INT          NOT NULL,
  prioridade_default  VARCHAR(20)  NOT NULL,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
