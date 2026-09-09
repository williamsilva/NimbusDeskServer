-- Configurações > Categorias (com.nimbusdesk.tickets.model.Category). sla_id é uma coluna crua com
-- FK de banco (não @ManyToOne) - ver Category.java. O SLA de um chamado é sempre copiado de
-- categoria.sla_id na abertura, nunca setado manualmente (ver PROJECT_SPEC.md).
CREATE TABLE categories (
  id         UUID         PRIMARY KEY,
  nome       VARCHAR(120) NOT NULL,
  sla_id     UUID         NOT NULL REFERENCES slas (id),
  ativo      BOOLEAN      NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX categories_sla_id_idx ON categories (sla_id);
