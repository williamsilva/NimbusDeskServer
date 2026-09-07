package com.nimbusdesk.common.web;

import com.nimbussystems.commons.web.SearchRequest;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Converte a paginação/ordenação crua de {@link SearchRequest} num {@link Pageable} real do
 *  Spring Data, para uso com {@code repository.findAll(spec, pageable)}. */
public final class PageableMapper {
  private PageableMapper() {
  }

  /** @param defaultSort usado quando a request não trouxer nenhum sort explícito - cada tela
   *  tem seu próprio campo/direção padrão (ex.: Email Log por sentAt desc, Usuários por name
   *  asc), então não há um default único genérico. */
  public static Pageable toPageable(
      Integer page, Integer size, List<SearchRequest.SortItem> sort, Sort defaultSort) {
    int pageNumber = (page == null || page < 0) ? 0 : page;
    int pageSize = (size == null || size <= 0) ? 20 : size;

    Sort resolved = toSort(sort);
    return PageRequest.of(pageNumber, pageSize, resolved.isSorted() ? resolved : defaultSort);
  }

  private static Sort toSort(List<SearchRequest.SortItem> sort) {
    if (sort == null || sort.isEmpty()) {
      return Sort.unsorted();
    }

    var orders = sort.stream()
        .filter(x -> x != null && x.field() != null && !x.field().isBlank() && x.order() != null)
        .map(x -> new Sort.Order(x.order() == 1 ? Sort.Direction.ASC : Sort.Direction.DESC, x.field()))
        .toList();

    return orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
  }
}
