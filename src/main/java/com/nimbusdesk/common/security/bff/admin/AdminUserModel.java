package com.nimbusdesk.common.security.bff.admin;

import com.nimbussystems.commons.security.bff.admin.AdminUserResponse;

import com.nimbussystems.commons.security.bff.admin.AdminUserMinimalResponse;

import com.nimbussystems.commons.security.bff.admin.AdminGroupOptionResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/** Mesmo shape de {@link AdminUserResponse} - usado só pelo endpoint de busca paginada, um record
 *  "Response" simples não serve de {@link RepresentationModel} (precisa ser uma classe mutável
 *  sem construtor de argumentos, ver {@code PagedResourcesAssembler}). Dado remoto (NimbusCore via
 *  HTTP) - sem Specification/JPA possível aqui, só o envelope de resposta muda pra ficar
 *  consistente com as demais telas (ver AdminUserService.search). */
@Getter
@Setter
@NoArgsConstructor
@Relation(collectionRelation = "content")
public class AdminUserModel extends RepresentationModel<AdminUserModel> {

  private UUID id;
  private String name;
  private String userName;
  private String document;
  private Integer status;
  private Instant createdAt;
  private Instant lastLoginAt;
  private Instant blockedUntil;
  private Instant passwordExpiresAt;
  private AdminUserMinimalResponse createdBy;
  private List<AdminGroupOptionResponse> groups = List.of();
}
