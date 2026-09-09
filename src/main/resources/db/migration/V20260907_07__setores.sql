-- Configurações > Setores (com.nimbusdesk.tickets.model.Setor) - mesmo desenho do Department do
-- NimbusFlowServer. setor_users é a @CollectionTable do @ElementCollection Setor.userIds (só ids
-- crus do NimbusAuth, sem entidade própria de usuário local).
CREATE TABLE setores (
  id         UUID         PRIMARY KEY,
  nome       VARCHAR(120) NOT NULL,
  ativo      BOOLEAN      NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE setor_users (
  setor_id UUID         NOT NULL REFERENCES setores (id) ON DELETE CASCADE,
  user_id  VARCHAR(100) NOT NULL
);

CREATE INDEX setor_users_setor_id_idx ON setor_users (setor_id);
