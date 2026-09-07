-- Tabela exigida pela entidade compartilhada com.nimbussystems.commons.security.UserDirectoryEntity
-- (lib nimbus-commons-server) - cache local read-only do id/nome/username de usuários do
-- NimbusAuth, usado só pra exibir "solicitado por"/"aprovado por" etc. sem bater na rede a cada
-- request. Sem FK: o id é gerenciado por um sistema/banco separado (NimbusAuth). Mesmo shape do
-- NimbusNovaxServer (V20260812_03__user_directory.sql).
CREATE TABLE user_directory (
  id UUID NOT NULL,
  username VARCHAR(120) NOT NULL,
  name VARCHAR(120) NOT NULL,
  synced_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id)
);
