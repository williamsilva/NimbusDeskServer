package com.nimbusdesk.common.web.spec;

import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;

/**
 * Helpers de {@link Specification} (JPA Criteria) reaproveitáveis entre entidades — mesmo espírito
 * de {@code BaseSpecificationSupport} do CardSync, mas reduzida ao que as specs do NimbusDesk
 * realmente precisam: os valores de filtro (texto, listas, ranges de data) já chegam RESOLVIDOS
 * por {@code com.nimbussystems.commons.web.FilterSupport} (mesma extração usada hoje pelo filtro em
 * memória) — não há necessidade de portar {@code SpecificationFactory}/{@code FieldSpec}/
 * {@code DateFilterService} do CardSync, que existem lá para resolver o mesmo problema que o
 * {@code FilterSupport} já resolve aqui.
 */
public abstract class BaseSpecificationSupport<T> {

  protected Specification<T> alwaysTrue() {
    return Specs.all();
  }

  protected Specification<T> contains(String value, String field) {
    if (isBlank(value)) {
      return alwaysTrue();
    }

    String normalized = normalize(value);
    return (root, query, cb) -> cb.like(cb.lower(root.get(field).as(String.class)), like(normalized));
  }

  /** Mesmo que {@link #contains(String, String)}, mas buscando num atributo de uma associação
   *  (LEFT JOIN) — ex.: buscar pedido pelo nome do cliente (order.client.name). */
  protected Specification<T> containsPath(String value, String association, String field) {
    if (isBlank(value)) {
      return alwaysTrue();
    }

    String normalized = normalize(value);
    return (root, query, cb) -> {
      Expression<String> path = cb.lower(root.join(association, JoinType.LEFT).get(field).as(String.class));
      return cb.like(path, like(normalized));
    };
  }

  protected <V> Specification<T> equalsTo(String field, V value) {
    if (value == null) {
      return alwaysTrue();
    }

    return (root, query, cb) -> cb.equal(root.get(field), value);
  }

  protected <E> Specification<T> inCodes(String field, Collection<E> values, Function<E, ?> mapper) {
    if (values == null || values.isEmpty()) {
      return alwaysTrue();
    }

    var mapped = values.stream().filter(Objects::nonNull).map(mapper).filter(Objects::nonNull).toList();
    if (mapped.isEmpty()) {
      return alwaysTrue();
    }

    return (root, query, cb) -> root.get(field).in(mapped);
  }

  /** Mesmo que {@link #inCodes}, mas resolvendo o campo via associação (LEFT JOIN) — ex.: filtrar
   *  registros por uma lista de ids de um relacionamento. */
  protected <V> Specification<T> inPath(Collection<V> values, Function<V, ?> mapper, String association, String field) {
    if (values == null || values.isEmpty()) {
      return alwaysTrue();
    }

    var mapped = values.stream().filter(Objects::nonNull).map(mapper).filter(Objects::nonNull).toList();
    if (mapped.isEmpty()) {
      return alwaysTrue();
    }

    return (root, query, cb) -> root.join(association, JoinType.LEFT).get(field).in(mapped);
  }

  /** Range já resolvido (ver {@code FilterSupport.periodLocalDateRange}) — from/to nulos são
   *  tratados como "sem limite" naquela ponta. */
  protected Specification<T> rangeLocalDate(String field, LocalDate from, LocalDate to) {
    if (from == null && to == null) {
      return alwaysTrue();
    }

    return (root, query, cb) -> {
      Path<LocalDate> path = root.get(field);
      if (from != null && to != null) {
        return cb.between(path, from, to);
      }
      return from != null ? cb.greaterThanOrEqualTo(path, from) : cb.lessThanOrEqualTo(path, to);
    };
  }

  /** Range já resolvido (ver {@code FilterSupport.periodInstantRange}). */
  protected Specification<T> rangeInstant(String field, Instant from, Instant to) {
    if (from == null && to == null) {
      return alwaysTrue();
    }

    return (root, query, cb) -> {
      Path<Instant> path = root.get(field);
      if (from != null && to != null) {
        return cb.between(path, from, to);
      }
      return from != null ? cb.greaterThanOrEqualTo(path, from) : cb.lessThanOrEqualTo(path, to);
    };
  }

  protected Specification<T> orderByAsc(String field) {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        query.orderBy(cb.asc(root.get(field)));
      }
      return cb.conjunction();
    };
  }

  protected Specification<T> orderByDesc(String field) {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        query.orderBy(cb.desc(root.get(field)));
      }
      return cb.conjunction();
    };
  }

  protected boolean isCountQuery(CriteriaQuery<?> query) {
    return Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
  }

  /** Reaproveita um fetch já aberto pra essa associação em vez de abrir um segundo (evita
   *  duplicar o JOIN quando mais de uma Specification faz fetch da mesma relação). */
  protected static Fetch<?, ?> fetchIfNotFetched(Root<?> root, String attributeName) {
    return fetchIfNotFetched((FetchParent<?, ?>) root, attributeName);
  }

  protected static Fetch<?, ?> fetchIfNotFetched(FetchParent<?, ?> parent, String attributeName) {
    for (Fetch<?, ?> fetch : parent.getFetches()) {
      if (attributeName.equals(fetch.getAttribute().getName())) {
        return fetch;
      }
    }
    return parent.fetch(attributeName, JoinType.LEFT);
  }

  /** Reaproveita um join/fetch já aberto pra essa associação — necessário pra ordenar por uma
   *  coluna de uma associação já trazida via {@link #fetchIfNotFetched}, sem abrir um segundo
   *  LEFT JOIN pra mesma tabela (getJoins() e getFetches() são coleções separadas na Criteria
   *  API). */
  protected Join<?, ?> join(From<?, ?> from, String attribute) {
    for (Join<?, ?> j : from.getJoins()) {
      if (j.getAttribute().getName().equals(attribute) && j.getJoinType() == JoinType.LEFT) {
        return j;
      }
    }

    for (Fetch<?, ?> fetch : from.getFetches()) {
      if (fetch.getAttribute().getName().equals(attribute) && fetch.getJoinType() == JoinType.LEFT
          && fetch instanceof Join<?, ?> fetchAsJoin) {
        return fetchAsJoin;
      }
    }

    return from.join(attribute, JoinType.LEFT);
  }

  protected boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  protected String normalize(String value) {
    return value.trim().toLowerCase();
  }

  protected String like(String value) {
    return "%" + value + "%";
  }
}
