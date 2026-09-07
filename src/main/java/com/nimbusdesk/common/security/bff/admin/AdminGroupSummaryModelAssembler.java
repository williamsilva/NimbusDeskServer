package com.nimbusdesk.common.security.bff.admin;

import com.nimbussystems.commons.security.bff.admin.AdminGroupSummaryResponse;

import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/** Extends {@code RepresentationModelAssembler} puro - ver {@link AdminUserModelAssembler} para
 *  o porquê. */
@Component
public class AdminGroupSummaryModelAssembler
    implements RepresentationModelAssembler<AdminGroupSummaryResponse, AdminGroupSummaryModel> {

  @Override
  public AdminGroupSummaryModel toModel(AdminGroupSummaryResponse response) {
    AdminGroupSummaryModel model = new AdminGroupSummaryModel();

    model.setId(response.id());
    model.setName(response.name());
    model.setDescription(response.description());
    model.setUsersCount(response.usersCount());
    model.setPermissionsCount(response.permissionsCount());
    model.setCreatedAt(response.createdAt());
    model.setCreatedBy(response.createdBy());

    return model;
  }
}
