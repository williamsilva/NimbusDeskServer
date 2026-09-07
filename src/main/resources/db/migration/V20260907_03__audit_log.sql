-- Tabela exigida pela entidade compartilhada com.nimbussystems.commons.audit.AuditLog (lib
-- nimbus-commons-server) - escaneada via CommonsIntegrationConfig mesmo sem nenhum código do
-- NimbusDesk usar @Auditable ainda (ddl-auto=validate exige a tabela existir de qualquer forma).
-- Mesmo shape usado em CardsyncServer/NimbusFlowServer/NimbusNovaxServer (ver
-- V20260805_01__audit_log.sql no NimbusNovaxServer) - log de auditoria genérico ("quem, quando, o
-- quê, de/para qual estado") pra ações sensíveis futuras, reaproveitável por qualquer módulo.
CREATE TABLE audit_logs (
  id UUID PRIMARY KEY,
  entity_name VARCHAR(100) NOT NULL,
  entity_id VARCHAR(100),
  action VARCHAR(50) NOT NULL,
  user_id VARCHAR(100),
  data_before TEXT,
  data_after TEXT,
  timestamp TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_logs_entity_idx ON audit_logs (entity_name, entity_id);
CREATE INDEX audit_logs_timestamp_idx ON audit_logs (timestamp);
