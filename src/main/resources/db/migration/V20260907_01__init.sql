-- Baseline do NimbusDesk (2026-09-07). Base mínima: Configuração de E-mail (email_settings, vem
-- pronta do NimbusCommonsServer, ver EmailSettingsEntity) + Auditoria de E-mail (email_log, código
-- local, ver com.nimbusdesk.common.notification.mail.EmailLogService/EmailLogEntity da lib).
-- Sem PostGIS - o NimbusDesk não usa nenhum tipo espacial.

-- Linha única (mesmo padrão de email_settings no NimbusAuth/CardsyncServer/NimbusFlowServer/
-- NimbusNovaxServer) - EmailSettingsRepository.findFirstBy() sempre lê/atualiza a mesma linha.
CREATE TABLE email_settings (
  id             UUID         PRIMARY KEY,
  impl           VARCHAR(10),
  from_name      VARCHAR(255),
  from_email     VARCHAR(255),
  brevo_api_key  VARCHAR(500),
  brevo_base_url VARCHAR(255),
  brevo_port     INT,
  brevo_username VARCHAR(255),
  smtp_host      VARCHAR(255),
  smtp_port      INT,
  smtp_username  VARCHAR(255),
  smtp_password  VARCHAR(500),
  smtp_auth      BOOLEAN,
  smtp_starttls  BOOLEAN,
  smtp_ssl       BOOLEAN,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Auditoria de envio (sucesso/erro) - shape final já com recipients TEXT (todos os destinatários,
-- não só o primeiro) e body TEXT (corpo HTML renderizado), mesmo papel de email_log no
-- NimbusAuth/CardsyncServer/NimbusFlowServer/NimbusNovaxServer.
CREATE TABLE email_log (
  id              UUID         PRIMARY KEY,
  event_type      VARCHAR(60)  NOT NULL,
  recipients      TEXT         NOT NULL,
  subject         VARCHAR(300) NOT NULL,
  template        VARCHAR(200) NOT NULL,
  body            TEXT,
  status          VARCHAR(10)  NOT NULL,
  error_message   VARCHAR(1000),
  requested_by_id VARCHAR(100),
  sent_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX email_log_sent_at_idx ON email_log (sent_at);
CREATE INDEX email_log_status_idx ON email_log (status);
CREATE INDEX email_log_event_type_idx ON email_log (event_type);
