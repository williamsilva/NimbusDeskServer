package com.nimbusdesk.common.security.bff.admin;

import com.nimbussystems.commons.security.bff.admin.AdminUserResponse;

import java.util.List;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Extends {@code RepresentationModelAssembler} puro - ver
 *  {@code com.nimbusdesk.common.notification.mail.EmailLogModelAssembler} para o porquê (sem
 *  entidade JPA local pra montar um link self via reflexão de forma confiável aqui). */
@Component
public class AdminUserModelAssembler implements RepresentationModelAssembler<AdminUserResponse, AdminUserModel> {

  @Override
  public AdminUserModel toModel(AdminUserResponse response) {
    AdminUserModel model = new AdminUserModel();

    model.setId(response.id());
    model.setName(response.name());
    model.setUserName(response.userName());
    model.setDocument(response.document());
    model.setStatus(response.status());
    model.setCreatedAt(response.createdAt());
    model.setLastLoginAt(response.lastLoginAt());
    model.setBlockedUntil(response.blockedUntil());
    model.setPasswordExpiresAt(response.passwordExpiresAt());
    model.setCreatedBy(response.createdBy());
    model.setGroups(response.groups() == null ? List.of() : response.groups());

    return model;
  }
}
