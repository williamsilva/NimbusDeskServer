package com.nimbusdesk.common.notification.mail;

import com.nimbussystems.commons.notification.mail.EmailLogEntity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmailLogRepository
    extends JpaRepository<EmailLogEntity, UUID>, JpaSpecificationExecutor<EmailLogEntity> {
}
