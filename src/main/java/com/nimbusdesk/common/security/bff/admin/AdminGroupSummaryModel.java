package com.nimbusdesk.common.security.bff.admin;

import com.nimbussystems.commons.security.bff.admin.AdminUserMinimalResponse;

import com.nimbussystems.commons.security.bff.admin.AdminGroupSummaryResponse;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/** Mesmo shape de {@link AdminGroupSummaryResponse} - ver
 *  {@code com.nimbusdesk.common.security.bff.admin.AdminUserModel} para o porquê dessa
 *  separação e da ausência de Specification/JPA aqui (dado remoto, NimbusCore via HTTP). */
@Getter
@Setter
@NoArgsConstructor
@Relation(collectionRelation = "content")
public class AdminGroupSummaryModel extends RepresentationModel<AdminGroupSummaryModel> {

  private UUID id;
  private String name;
  private String description;
  private Integer usersCount;
  private Integer permissionsCount;
  private Instant createdAt;
  private AdminUserMinimalResponse createdBy;
}
