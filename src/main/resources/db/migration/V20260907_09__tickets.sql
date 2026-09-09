-- Chamado de TI (com.nimbusdesk.tickets.model.Ticket) - ver PROJECT_SPEC.md "Modelo de domínio".
-- categoria_id/sla_id/setor_id/equipamento_ref_id são colunas cruas com FK de banco (sem
-- @ManyToOne) - mesma técnica de referência entre agregados já usada em todo o NimbusFlow.

-- Sequencial pro número visível pro usuário ("CH-2026-00001", ver TicketService#nextNumero) -
-- sequence ÚNICA e sempre-crescente (não reseta por ano, decisão de design documentada no código).
CREATE SEQUENCE ticket_numero_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE tickets (
  id                 UUID         PRIMARY KEY,
  numero             VARCHAR(20)  NOT NULL,
  titulo             VARCHAR(200) NOT NULL,
  descricao          VARCHAR(2000) NOT NULL,
  status             VARCHAR(30)  NOT NULL,
  prioridade         VARCHAR(20)  NOT NULL,
  categoria_id       UUID         NOT NULL REFERENCES categories (id),
  sla_id             UUID         NOT NULL REFERENCES slas (id),
  setor_id           UUID         REFERENCES setores (id),
  solicitante_id     VARCHAR(100) NOT NULL,
  responsavel_id     VARCHAR(100),
  equipamento_ref_id UUID         REFERENCES equipamento_ref (id),
  canal_origem       VARCHAR(20)  NOT NULL DEFAULT 'PORTAL',
  data_abertura      TIMESTAMPTZ  NOT NULL,
  data_limite_sla    TIMESTAMPTZ  NOT NULL,
  data_resolucao     TIMESTAMPTZ,
  data_fechamento    TIMESTAMPTZ,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX tickets_numero_idx ON tickets (numero);
CREATE INDEX tickets_status_idx ON tickets (status);
CREATE INDEX tickets_categoria_id_idx ON tickets (categoria_id);
CREATE INDEX tickets_setor_id_idx ON tickets (setor_id);
CREATE INDEX tickets_solicitante_id_idx ON tickets (solicitante_id);
CREATE INDEX tickets_responsavel_id_idx ON tickets (responsavel_id);
CREATE INDEX tickets_data_limite_sla_idx ON tickets (data_limite_sla);
