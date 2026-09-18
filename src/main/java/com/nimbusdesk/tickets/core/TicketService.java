package com.nimbusdesk.tickets.core;

import com.nimbusdesk.common.security.NdSecurity;
import com.nimbusdesk.common.security.NimbusCoreInternalClient;
import com.nimbussystems.commons.security.CurrentUserProvider;
import com.nimbussystems.commons.security.UserDirectoryService;
import com.nimbussystems.commons.security.bff.admin.AdminUserMinimalResponse;
import com.nimbussystems.commons.web.FilterSupport;
import com.nimbussystems.commons.web.PageResponse;
import com.nimbussystems.commons.web.SearchRequest;
import com.nimbusdesk.tickets.dto.request.TicketAssignRequest;
import com.nimbusdesk.tickets.dto.request.TicketParticipantsRequest;
import com.nimbusdesk.tickets.dto.request.TicketRequest;
import com.nimbusdesk.tickets.dto.request.TicketStatusRequest;
import com.nimbusdesk.tickets.dto.response.TicketParticipantResponse;
import com.nimbusdesk.tickets.dto.response.TicketResponse;
import com.nimbusdesk.tickets.dto.response.TicketScopeCountsResponse;
import com.nimbusdesk.tickets.dto.response.TicketStatusHistoryResponse;
import com.nimbusdesk.tickets.model.Category;
import com.nimbusdesk.tickets.model.EquipamentoRef;
import com.nimbusdesk.tickets.model.Setor;
import com.nimbusdesk.tickets.model.Sla;
import com.nimbusdesk.tickets.model.Ticket;
import com.nimbusdesk.tickets.model.TicketChannel;
import com.nimbusdesk.tickets.model.TicketParticipant;
import com.nimbusdesk.tickets.model.TicketStatus;
import com.nimbusdesk.tickets.model.TicketStatusHistory;
import com.nimbusdesk.tickets.repository.CategoryRepository;
import com.nimbusdesk.tickets.repository.EquipamentoRefRepository;
import com.nimbusdesk.tickets.repository.SetorRepository;
import com.nimbusdesk.tickets.repository.SlaRepository;
import com.nimbusdesk.tickets.repository.TicketParticipantRepository;
import com.nimbusdesk.tickets.repository.TicketRepository;
import com.nimbusdesk.tickets.repository.TicketStatusHistoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Chamado de TI (helpdesk) - ver PROJECT_SPEC.md pro contrato completo de domínio/endpoints.
 * Estilo de busca/filtro/paginação (in-memory, via {@link SearchRequest}/{@link PageResponse}/
 * {@link FilterSupport}) espelha exatamente com.nimbusflow.tickets.core.TicketService#search -
 * mesmo racional de volume pequeno o suficiente pra não precisar de Specification+Pageable de
 * verdade nesta fase (decisão de design: NimbusDeskServer já tem esse padrão mais "moderno"
 * disponível, ver EmailLogService/AdminUserService, mas o pacote com.nimbusflow.tickets foi
 * explicitamente apontado como template arquitetural pra este módulo - seguido à risca aqui).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TicketService {

  /** Único ponto de verdade em TicketStatusTransitions - reaproveitado aqui pra não duplicar (ver
   *  Javadoc lá). */
  private static final Set<TicketStatus> TERMINAL_STATUSES = TicketStatusTransitions.TERMINAL_STATUSES;
  /** 2026-09-08: só RESOLVIDO reabre mais - FECHADO -> EM_ANDAMENTO foi removido de
   *  TicketStatusTransitions (decisão do usuário: fechado/cancelado não mudam mais de status),
   *  então esse branch nunca mais dispara pra FECHADO - mantido aqui só por precisão/clareza. */
  private static final Set<TicketStatus> REOPEN_FROM = Set.of(TicketStatus.RESOLVIDO);

  /** Mesmos valores de TicketNotificationService#NIMBUSDESK_APP_KEY/CHAMADO_MANAGE_PERMISSION -
   *  usados aqui só por #responsavelOptions (ver Javadoc lá). */
  private static final String NIMBUSDESK_APP_KEY = "nimbusdesk";
  private static final String CHAMADO_MANAGE_PERMISSION = "CHAMADO_MANAGE";

  /** usuarioId de TicketStatusHistory pras transições feitas por #autoCloseResolved (job de
   *  automação, sem usuário logado/CurrentUserProvider) - raw string sem FK, mesma técnica de
   *  solicitanteId/responsavelId. */
  private static final String SYSTEM_ACTOR_ID = "SYSTEM";

  private final TicketRepository ticketRepository;
  private final CategoryRepository categoryRepository;
  private final SlaRepository slaRepository;
  private final SetorRepository setorRepository;
  private final EquipamentoRefRepository equipamentoRefRepository;
  private final TicketStatusHistoryRepository historyRepository;
  private final TicketParticipantRepository participantRepository;
  private final TicketCommentService ticketCommentService;
  private final TicketEventBroadcaster ticketEventBroadcaster;
  private final TicketNotificationService ticketNotificationService;
  private final NimbusCoreInternalClient nimbusCoreInternalClient;
  private final CurrentUserProvider currentUserProvider;
  private final UserDirectoryService userDirectoryService;
  private final NdSecurity ndSecurity;

  /**
   * {@code advanced.scope} controla a base da busca (ver PROJECT_SPEC.md e
   * TicketsPermissionPolicy no frontend): {@code mine} (default) - solicitante=eu OU
   * responsável=eu, sem gate; {@code waiting} - "de quem é a vez de agir" sou eu (ver
   * Ticket#aguardandoAcaoDe), sem gate (só pode estar aguardando ação num chamado onde já sou
   * parte); {@code shared} - sou participante (ver TicketParticipant), sem gate; {@code queue} -
   * "fila de atendimento" (ABERTO, sem responsável, mais antigo primeiro - FIFO), exige
   * CHAMADO_MANAGE (só quem pode assumir/tratar um chamado precisa ver a fila); {@code all} -
   * todos os chamados, exige CHAMADO_CONSULT.
   */
  @Transactional(readOnly = true)
  public PageResponse<TicketResponse> search(SearchRequest request) {
    Map<String, Object> advanced = request.advanced() == null ? Map.of() : request.advanced();
    String scope = String.valueOf(advanced.getOrDefault("scope", "mine"));

    List<Ticket> base = switch (scope) {
      case "waiting" -> ticketRepository.findByAguardandoAcaoDeOrderByCreatedAtDesc(currentUserProvider.requireUserId());
      case "shared" -> {
        List<UUID> ids = participantRepository.findTicketIdsByUserId(currentUserProvider.requireUserId());
        yield ids.isEmpty() ? List.of() : ticketRepository.findByIdInOrderByCreatedAtDesc(ids);
      }
      case "queue" -> {
        requireManage();
        yield ticketRepository.findByStatusAndResponsavelIdIsNullOrderByDataAberturaAsc(TicketStatus.ABERTO);
      }
      case "all" -> {
        requireConsult();
        yield ticketRepository.findAllByOrderByCreatedAtDesc();
      }
      default -> {
        String userId = currentUserProvider.requireUserId();
        yield ticketRepository.findBySolicitanteIdOrResponsavelIdOrderByCreatedAtDesc(userId, userId);
      }
    };

    List<TicketResponse> all = toResponseList(base);
    List<TicketResponse> filtered = filterTickets(all, request, advanced);
    List<TicketResponse> sorted = sortTickets(filtered, request, scope);

    int page = request.page() == null ? 0 : Math.max(0, request.page());
    int size = request.size() == null || request.size() <= 0 ? 20 : request.size();
    int from = Math.min(page * size, sorted.size());
    int to = Math.min(from + size, sorted.size());

    return PageResponse.of(sorted.subList(from, to), page, size, sorted.size());
  }

  /**
   * Contagens pros badges dos 5 escopos na p-selectButton do frontend (ver TicketController#
   * scopeCounts) - dedicado (em vez de reaproveitar #search com {@code size=1}) pra não pagar o
   * custo de montar TicketResponse inteiro (resolução de nome/categoria/etc) só pra saber um
   * total. Todas excluem chamados "encerrados" ({@link #TERMINAL_STATUSES}) - o badge só reflete
   * trabalho ainda em aberto.
   *
   * <p>{@code queue}/{@code all} somem (0) pra quem não tem CHAMADO_MANAGE/CHAMADO_CONSULT
   * respectivamente - mesmo gate desses escopos em #search, só que devolvendo 0 em vez de 403: o
   * frontend já esconde a aba nesse caso (ver TicketsListComponent#canSeeQueue/canToggleScope),
   * não faz sentido travar a tela inteira por causa de um badge que nem vai aparecer.
   * {@code mine}/{@code waiting}/{@code shared} nunca têm gate (mesmo racional de #search: só
   * posso estar "aguardando ação"/ser dono, responsável ou participante de um chamado onde já sou
   * parte).
   *
   * <p>{@code all} conta direto (não soma mine+waiting+shared+queue) - um chamado pode satisfazer
   * mais de um escopo ao mesmo tempo (ex. sou solicitante E estou aguardando ação nele), então a
   * soma contava ele em dobro.
   */
  @Transactional(readOnly = true)
  public TicketScopeCountsResponse scopeCounts() {
    String userId = currentUserProvider.requireUserId();
    long mine = ticketRepository.countActiveMine(userId, TERMINAL_STATUSES);
    long waiting = ticketRepository.countActiveWaiting(userId, TERMINAL_STATUSES);
    long queue = ndSecurity.canManageChamados()
        ? ticketRepository.countByStatusAndResponsavelIdIsNull(TicketStatus.ABERTO)
        : 0;

    List<UUID> sharedIds = participantRepository.findTicketIdsByUserId(userId);
    long shared = sharedIds.isEmpty() ? 0 : ticketRepository.countActiveByIdIn(sharedIds, TERMINAL_STATUSES);

    long all = ndSecurity.canConsultChamados() ? ticketRepository.countByStatusNotIn(TERMINAL_STATUSES) : 0;

    return new TicketScopeCountsResponse(mine, waiting, shared, queue, all);
  }

  /**
   * Usuários com CHAMADO_MANAGE (T.I. - TECNICO_TI/GESTOR_TI/ADMIN_HELPDESK, ver PROJECT_SPEC.md
   * "Papéis") pro seletor "Atribuir responsável" da tela de detalhe (ver TicketDetailComponent) -
   * 2026-09-08, decisão do usuário: só técnico de verdade pode ser responsável de um chamado, não
   * SOLICITANTE nenhum (que também é "usuário do NimbusDesk", mas sem CHAMADO_MANAGE). Mesmo
   * mecanismo (fetchOptionsByPermission) já usado por TicketNotificationService#resolveTiUserIds
   * pra decidir quem notificar na abertura - aqui só devolve os dados pro combo, sem notificar
   * ninguém. Requer CHAMADO_MANAGE pra chamar (só quem já gerencia chamados atribui responsável,
   * mesmo gate de #assign).
   */
  @Transactional(readOnly = true)
  public List<AdminUserMinimalResponse> responsavelOptions() {
    if (!ndSecurity.canManageChamados()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing CHAMADO_MANAGE authority");
    }

    try {
      return nimbusCoreInternalClient.fetchOptionsByPermission(NIMBUSDESK_APP_KEY, CHAMADO_MANAGE_PERMISSION).stream()
          .map(u -> new AdminUserMinimalResponse(u.id(), u.name(), u.username()))
          .sorted(Comparator.comparing(AdminUserMinimalResponse::name, String.CASE_INSENSITIVE_ORDER))
          .toList();
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to resolve CHAMADO_MANAGE users from NimbusCore", e);
    }
  }

  private List<TicketResponse> filterTickets(List<TicketResponse> items, SearchRequest request, Map<String, Object> advanced) {
    List<String> statuses = FilterSupport.listFilter(request.tableFilters(), advanced, "status", "status");
    List<String> prioridades = FilterSupport.listFilter(request.tableFilters(), advanced, "prioridade", "prioridade");
    UUID categoriaId = readUuid(advanced, "categoriaId");
    UUID setorId = readUuid(advanced, "setorId");
    String global = request.globalFilter();

    return items.stream()
        .filter(t -> statuses.isEmpty() || statuses.contains(t.status().name()))
        .filter(t -> prioridades.isEmpty() || prioridades.contains(t.prioridade().name()))
        .filter(t -> categoriaId == null || categoriaId.equals(t.categoriaId()))
        .filter(t -> setorId == null || setorId.equals(t.setorId()))
        .filter(t -> global == null || global.isBlank()
            || FilterSupport.containsIgnoreCase(t.titulo(), global)
            || FilterSupport.containsIgnoreCase(t.descricao(), global)
            || FilterSupport.containsIgnoreCase(t.numero(), global))
        .toList();
  }

  private UUID readUuid(Map<String, Object> advanced, String key) {
    Object raw = advanced == null ? null : advanced.get(key);
    if (raw == null) {
      return null;
    }
    try {
      return UUID.fromString(String.valueOf(raw));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * {@code scope} só influencia o DEFAULT (sem sort explícito no request) - "queue" ordena mais
   * antigo primeiro (FIFO, faz sentido pra fila de atendimento: quem chegou primeiro é atendido
   * primeiro), todos os outros escopos mantêm o padrão de sempre (mais recente primeiro). Um sort
   * explícito do usuário (clique em cabeçalho de coluna) sempre tem prioridade sobre esse default.
   */
  private List<TicketResponse> sortTickets(List<TicketResponse> items, SearchRequest request, String scope) {
    SearchRequest.SortItem sortItem =
        (request.sort() == null || request.sort().isEmpty()) ? null : request.sort().get(0);
    String field = sortItem != null && sortItem.field() != null ? sortItem.field() : "dataAbertura";
    boolean defaultDesc = !"queue".equals(scope);
    boolean desc = sortItem == null || sortItem.order() == null ? defaultDesc : sortItem.order() < 0;

    Comparator<TicketResponse> comparator = switch (field) {
      case "numero" -> Comparator.comparing(TicketResponse::numero, String.CASE_INSENSITIVE_ORDER);
      case "titulo" -> Comparator.comparing(TicketResponse::titulo, String.CASE_INSENSITIVE_ORDER);
      case "status" -> Comparator.comparing(t -> t.status().name());
      case "prioridade" -> Comparator.comparing(t -> t.prioridade().name());
      case "dataLimiteSla" -> Comparator.comparing(t -> orEpoch(t.dataLimiteSla()));
      case "updatedAt" -> Comparator.comparing(t -> orEpoch(t.updatedAt()));
      default -> Comparator.comparing(t -> orEpoch(t.dataAbertura()));
    };

    if (desc) {
      comparator = comparator.reversed();
    }
    return items.stream().sorted(comparator).toList();
  }

  private static Instant orEpoch(Instant value) {
    return value == null ? Instant.EPOCH : value;
  }

  /** Visível se for "meu" (solicitante ou responsável) OU se tiver CHAMADO_CONSULT. */
  @Transactional(readOnly = true)
  public TicketResponse getById(UUID id) {
    Ticket ticket = getOrThrow(id);
    requireView(ticket);
    return toResponse(ticket);
  }

  /**
   * Criação é livre - qualquer usuário autenticado pode abrir um chamado, sem gate de permissão.
   * Calcula numero sequencial, dataAbertura=now, dataLimiteSla a partir do SLA da categoria, status
   * inicial ABERTO. Sem linha de TicketStatusHistory na criação (não há "transição" nenhuma - ver
   * TicketStatusHistory#statusAnterior).
   *
   * <p>2026-09-08: {@code descricao} não fica só no campo do Ticket - vira também o PRIMEIRO
   * comentário da conversa (ver TicketCommentService#createOpeningComment), junto com os anexos
   * enviados na abertura (que passam a estar vinculados a ESTE comentário, sem exceção - não existe
   * mais "anexo sem comentário").
   */
  public TicketResponse create(TicketRequest request, List<MultipartFile> attachments) {
    Category category = getCategoryOrThrow(request.categoriaId());
    Sla sla = getSlaOrThrow(category.getSlaId());
    validateSetor(request.setorId());
    validateEquipamentoRef(request.equipamentoRefId());

    Instant now = Instant.now();

    Ticket ticket = new Ticket();
    ticket.setNumero(nextNumero(now));
    ticket.setTitulo(request.titulo());
    ticket.setDescricao(request.descricao());
    ticket.setStatus(TicketStatus.ABERTO);
    ticket.setPrioridade(request.prioridade());
    ticket.setCategoriaId(category.getId());
    ticket.setSlaId(sla.getId());
    ticket.setSetorId(request.setorId());
    ticket.setSolicitanteId(currentUserProvider.requireUserId());
    ticket.setEquipamentoRefId(request.equipamentoRefId());
    ticket.setCanalOrigem(TicketChannel.PORTAL);
    ticket.setDataAbertura(now);
    ticket.setDataLimiteSla(now.plus(Duration.ofMinutes(sla.getTempoResolucaoMin())));

    ticket = ticketRepository.save(ticket);
    ticketCommentService.createOpeningComment(ticket, request.descricao(), attachments);
    saveParticipants(ticket, request.participantIds());
    ticketNotificationService.notifyCreated(ticket);
    ticketEventBroadcaster.broadcastCreated(ticket);

    return toResponse(ticket);
  }

  /**
   * Só permitido se status=ABERTO, exige ser o solicitante OU CHAMADO_MANAGE. Se a categoria
   * mudar, {@code slaId}/{@code dataLimiteSla} são recalculados a partir do novo SLA (decisão de
   * design: slaId é sempre derivado da categoria, nunca fica "preso" ao SLA antigo depois de uma
   * troca de categoria) - {@code dataAbertura} original é preservada como base do cálculo.
   */
  public TicketResponse update(UUID id, TicketRequest request) {
    Ticket ticket = getOrThrow(id);

    String currentUserId = currentUserProvider.requireUserId();
    boolean isRequester = currentUserId.equals(ticket.getSolicitanteId());
    if (!isRequester && !ndSecurity.canManageChamados()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester or CHAMADO_MANAGE can edit this ticket");
    }
    if (ticket.getStatus() != TicketStatus.ABERTO) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket status does not accept edits: " + ticket.getStatus());
    }

    Category category = getCategoryOrThrow(request.categoriaId());
    validateSetor(request.setorId());
    validateEquipamentoRef(request.equipamentoRefId());

    boolean categoryChanged = !category.getId().equals(ticket.getCategoriaId());

    ticket.setTitulo(request.titulo());
    ticket.setDescricao(request.descricao());
    ticket.setCategoriaId(category.getId());
    ticket.setSetorId(request.setorId());
    ticket.setEquipamentoRefId(request.equipamentoRefId());

    if (categoryChanged) {
      Sla sla = getSlaOrThrow(category.getSlaId());
      ticket.setSlaId(sla.getId());
      ticket.setDataLimiteSla(ticket.getDataAbertura().plus(Duration.ofMinutes(sla.getTempoResolucaoMin())));
    }

    ticket = ticketRepository.save(ticket);
    ticketEventBroadcaster.broadcastUpdated(ticket);
    return toResponse(ticket);
  }

  /** {@code ABERTO -> EM_ANDAMENTO} - a ÚNICA forma de fazer essa transição (ver
   *  TicketStatusTransitions, que deliberadamente não a inclui pro endpoint genérico de status). */
  public TicketResponse assign(UUID id, TicketAssignRequest request) {
    requireManage();
    Ticket ticket = getOrThrow(id);

    if (ticket.getStatus() != TicketStatus.ABERTO) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Only an ABERTO ticket can be assigned (ABERTO -> EM_ANDAMENTO): current status is " + ticket.getStatus());
    }

    TicketStatus previous = ticket.getStatus();
    ticket.setResponsavelId(request.responsavelId());
    ticket.setStatus(TicketStatus.EM_ANDAMENTO);
    // Acabou de ser atribuído - agora é a vez do técnico agir (investigar/responder).
    ticket.setAguardandoAcaoDe(request.responsavelId());
    ticket = ticketRepository.save(ticket);
    writeHistory(ticket, previous, TicketStatus.EM_ANDAMENTO);
    ticketNotificationService.notifyAssigned(ticket);
    ticketEventBroadcaster.broadcastUpdated(ticket);

    return toResponse(ticket);
  }

  /** Valida a transição via {@link TicketStatusTransitions}, grava TicketStatusHistory e seta
   *  dataResolucao/dataFechamento quando aplicável (ver #applyStatusSideEffects). */
  public TicketResponse changeStatus(UUID id, TicketStatusRequest request) {
    requireManage();
    Ticket ticket = getOrThrow(id);

    TicketStatus previous = ticket.getStatus();
    TicketStatus next = request.novoStatus();

    if (!TicketStatusTransitions.isAllowed(previous, next)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Invalid status transition: " + previous + " -> " + next);
    }

    ticket.setStatus(next);
    applyStatusSideEffects(ticket, previous, next);
    ticket = ticketRepository.save(ticket);
    writeHistory(ticket, previous, next);
    ticketNotificationService.notifyActivity(ticket, currentUserProvider.requireUserId(), "status");
    ticketEventBroadcaster.broadcastUpdated(ticket);

    return toResponse(ticket);
  }

  /**
   * RESOLVIDO seta dataResolucao; FECHADO seta dataFechamento. Reabertura (RESOLVIDO ->
   * EM_ANDAMENTO - 2026-09-08: FECHADO não reabre mais, ver TicketStatusTransitions) limpa os dois
   * marcos - decisão de design (não detalhada no PROJECT_SPEC.md): uma vez reaberto, o chamado não
   * está mais "resolvido", então esses timestamps deixariam de refletir a realidade se
   * preservados.
   *
   * <p>Também atualiza {@link Ticket#getAguardandoAcaoDe()} ("de quem é a vez de agir"): terminal
   * (RESOLVIDO/FECHADO/CANCELADO) = ninguém pendente; AGUARDANDO_SOLICITANTE = solicitante;
   * EM_ANDAMENTO vindo de AGUARDANDO_SOLICITANTE (solicitante respondeu/foi retomado) ou de
   * RESOLVIDO (reaberto) = responsável (se já atribuído).
   */
  private void applyStatusSideEffects(Ticket ticket, TicketStatus previous, TicketStatus next) {
    Instant now = Instant.now();
    if (next == TicketStatus.RESOLVIDO) {
      ticket.setDataResolucao(now);
      ticket.setAguardandoAcaoDe(null);
    } else if (next == TicketStatus.FECHADO) {
      ticket.setDataFechamento(now);
      ticket.setAguardandoAcaoDe(null);
    } else if (next == TicketStatus.CANCELADO) {
      ticket.setAguardandoAcaoDe(null);
    } else if (next == TicketStatus.AGUARDANDO_SOLICITANTE) {
      ticket.setAguardandoAcaoDe(ticket.getSolicitanteId());
    } else if (next == TicketStatus.AGUARDANDO_RESPOSTA) {
      ticket.setAguardandoAcaoDe(ticket.getResponsavelId());
    } else if (next == TicketStatus.EM_ANDAMENTO
        && (previous == TicketStatus.AGUARDANDO_SOLICITANTE || previous == TicketStatus.AGUARDANDO_RESPOSTA
            || REOPEN_FROM.contains(previous))) {
      ticket.setAguardandoAcaoDe(ticket.getResponsavelId());
    }

    if (next == TicketStatus.EM_ANDAMENTO && REOPEN_FROM.contains(previous)) {
      ticket.setDataResolucao(null);
      ticket.setDataFechamento(null);
    }
  }

  private void writeHistory(Ticket ticket, TicketStatus previous, TicketStatus next) {
    writeHistory(ticket, previous, next, currentUserProvider.requireUserId());
  }

  private void writeHistory(Ticket ticket, TicketStatus previous, TicketStatus next, String usuarioId) {
    TicketStatusHistory history = new TicketStatusHistory();
    history.setTicket(ticket);
    history.setStatusAnterior(previous);
    history.setStatusNovo(next);
    history.setUsuarioId(usuarioId);
    history.setChangedAt(Instant.now());
    historyRepository.save(history);
  }

  /**
   * Chamado só pelo job de auto-fechamento (com.nimbusdesk.tickets.core.TicketAutoCloseJob) -
   * RESOLVIDO -&gt; FECHADO sem usuário autenticado (contexto de job agendado, sem HttpSession/
   * CurrentUserProvider) - por isso não passa por {@link #changeStatus} (que exige CHAMADO_MANAGE
   * do usuário logado). Mesma mecânica (grava TicketStatusHistory, aplica side effects, notifica
   * SSE), só sem gate de permissão (não há usuário) e sem e-mail (o pedido do usuário em 2026-09-09
   * foi só a transição de status em si, sem notificação pra este caso - diferente de #changeStatus).
   */
  public void autoCloseResolved(UUID id) {
    Ticket ticket = getOrThrow(id);
    if (ticket.getStatus() != TicketStatus.RESOLVIDO) {
      // Corrida - já mudou de status desde a consulta do job (ex.: reaberto ou fechado manualmente
      // entre a busca e a execução) - ignora silenciosamente, não é um erro.
      return;
    }

    TicketStatus previous = ticket.getStatus();
    ticket.setStatus(TicketStatus.FECHADO);
    applyStatusSideEffects(ticket, previous, TicketStatus.FECHADO);
    ticket = ticketRepository.save(ticket);
    writeHistory(ticket, previous, TicketStatus.FECHADO, SYSTEM_ACTOR_ID);
    ticketEventBroadcaster.broadcastUpdated(ticket);
  }

  /** Mesma regra de visibilidade do chamado (ver #getById). */
  @Transactional(readOnly = true)
  public List<TicketStatusHistoryResponse> listHistory(UUID id) {
    Ticket ticket = getOrThrow(id);
    requireView(ticket);

    return historyRepository.findByTicketIdOrderByChangedAtAsc(id).stream()
        .map(this::toHistoryResponse)
        .toList();
  }

  private TicketStatusHistoryResponse toHistoryResponse(TicketStatusHistory h) {
    return new TicketStatusHistoryResponse(
        h.getId(), h.getTicket().getId(), h.getStatusAnterior(), h.getStatusNovo(),
        h.getUsuarioId(), resolveUserName(h.getUsuarioId()), h.getChangedAt());
  }

  /** Sequencial formatado ("CH-2026-00001"), via sequence do Postgres - decisão de design (não
   *  detalhada no PROJECT_SPEC.md, que só dá o exemplo de formato): a sequence é ÚNICA e
   *  sempre-crescente (não reseta por ano). Em 2027 a numeração continua de onde parou em 2026
   *  (ex.: CH-2027-00043) em vez de reiniciar em 00001 - resetar por ano exigiria uma sequence por
   *  ano ou uma tabela de contador com upsert, complexidade não justificada pelo volume esperado. */
  private String nextNumero(Instant dataAbertura) {
    long seq = ticketRepository.nextNumeroSequenceValue();
    int year = dataAbertura.atZone(ZoneOffset.UTC).getYear();
    return "CH-%d-%05d".formatted(year, seq);
  }

  private void validateSetor(UUID setorId) {
    if (setorId != null && !setorRepository.existsById(setorId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setor not found: " + setorId);
    }
  }

  private void validateEquipamentoRef(UUID equipamentoRefId) {
    if (equipamentoRefId != null && !equipamentoRefRepository.existsById(equipamentoRefId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EquipamentoRef not found: " + equipamentoRefId);
    }
  }

  private Category getCategoryOrThrow(UUID id) {
    return categoryRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found: " + id));
  }

  private Sla getSlaOrThrow(UUID id) {
    return slaRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "SLA not found for category: " + id));
  }

  private void requireConsult() {
    if (!ndSecurity.canConsultChamados()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing CHAMADO_CONSULT authority");
    }
  }

  private void requireManage() {
    if (!ndSecurity.canManageChamados()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing CHAMADO_MANAGE authority");
    }
  }

  private void requireView(Ticket ticket) {
    String userId = currentUserProvider.getCurrentUser().userId();
    boolean isParticipant = userId != null && participantRepository.existsByTicketIdAndUserId(ticket.getId(), userId);
    boolean visible = TicketVisibility.isMine(ticket, userId, isParticipant) || ndSecurity.canConsultChamados();
    if (!visible) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ticket not visible to current user");
    }
  }

  /**
   * Participantes ("compartilhado com") - lista quem pode ver o chamado, gerenciada por
   * {@link #replaceParticipants}. Mesma regra de visibilidade do chamado pra listar.
   */
  @Transactional(readOnly = true)
  public List<TicketParticipantResponse> listParticipants(UUID id) {
    Ticket ticket = getOrThrow(id);
    requireView(ticket);
    return toParticipantResponses(participantRepository.findByTicketIdOrderByCreatedAtAsc(id));
  }

  /**
   * Substitui a lista inteira de participantes (mesmo padrão "replace all" já usado em
   * Setor#userIds/grupos do NimbusCore) - exige ser o solicitante OU CHAMADO_MANAGE. O próprio
   * solicitante/responsável nunca precisa estar na lista (já são "meus chamados" por definição,
   * ver TicketVisibility) - se vierem no payload, são ignorados silenciosamente aqui.
   */
  public List<TicketParticipantResponse> replaceParticipants(UUID id, TicketParticipantsRequest request) {
    Ticket ticket = getOrThrow(id);

    String currentUserId = currentUserProvider.requireUserId();
    boolean isRequester = currentUserId.equals(ticket.getSolicitanteId());
    if (!isRequester && !ndSecurity.canManageChamados()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester or CHAMADO_MANAGE can manage participants");
    }

    participantRepository.deleteByTicketId(id);
    participantRepository.flush();

    List<TicketParticipant> saved = saveParticipants(ticket, request.userIds());

    ticketEventBroadcaster.broadcastUpdated(ticket);

    return toParticipantResponses(saved);
  }

  /** Filtra nulo/duplicado/o próprio solicitante-responsável (não faz sentido ser participante do
   *  próprio chamado, já é "meu chamado" por definição - ver TicketVisibility) e persiste. Sem
   *  {@code deleteByTicketId} aqui de propósito - quem chama decide se é "replace" (ver
   *  #replaceParticipants, que deleta antes) ou "primeira vez" (ver #create, chamado num ticket
   *  recém-criado, nunca tem participante nenhum ainda). */
  private List<TicketParticipant> saveParticipants(Ticket ticket, List<String> rawUserIds) {
    List<String> userIds = (rawUserIds == null ? List.<String>of() : rawUserIds).stream()
        .filter(Objects::nonNull)
        .distinct()
        .filter(userId -> !userId.equals(ticket.getSolicitanteId()) && !userId.equals(ticket.getResponsavelId()))
        .toList();

    return userIds.stream().map(userId -> {
      TicketParticipant p = new TicketParticipant();
      p.setTicket(ticket);
      p.setUserId(userId);
      return participantRepository.save(p);
    }).toList();
  }

  private List<TicketParticipantResponse> toParticipantResponses(List<TicketParticipant> participants) {
    return participants.stream()
        .map(p -> new TicketParticipantResponse(p.getUserId(), resolveUserName(p.getUserId())))
        .toList();
  }

  private Ticket getOrThrow(UUID id) {
    return ticketRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found: " + id));
  }

  /**
   * Resolução em lote (uma consulta por tabela pra todos os ids distintos da lista) pra
   * categoria/SLA/setor/equipamento (todos joins locais baratos) - evita reintroduzir o padrão N+1
   * já identificado como problema crítico neste workspace (ver memória de performance da
   * reconciliação ERP x Adquirente). solicitante/responsável continuam resolvidos por item via
   * UserDirectoryService (chamada externa já cacheada por usuário, mesmo padrão de
   * com.nimbusflow.tickets.core.TicketService#resolveReportedByName).
   */
  private List<TicketResponse> toResponseList(List<Ticket> tickets) {
    Set<UUID> categoriaIds = tickets.stream().map(Ticket::getCategoriaId).filter(Objects::nonNull).collect(Collectors.toSet());
    Map<UUID, Category> categoriesById = categoriaIds.isEmpty()
        ? Map.of() : categoryRepository.findAllById(categoriaIds).stream().collect(Collectors.toMap(Category::getId, c -> c));

    Set<UUID> slaIds = tickets.stream().map(Ticket::getSlaId).filter(Objects::nonNull).collect(Collectors.toSet());
    Map<UUID, String> slaNamesById = slaIds.isEmpty()
        ? Map.of() : slaRepository.findAllById(slaIds).stream().collect(Collectors.toMap(Sla::getId, Sla::getNome));

    Set<UUID> setorIds = tickets.stream().map(Ticket::getSetorId).filter(Objects::nonNull).collect(Collectors.toSet());
    Map<UUID, String> setorNamesById = setorIds.isEmpty()
        ? Map.of() : setorRepository.findAllById(setorIds).stream().collect(Collectors.toMap(Setor::getId, Setor::getNome));

    Set<UUID> equipIds = tickets.stream().map(Ticket::getEquipamentoRefId).filter(Objects::nonNull).collect(Collectors.toSet());
    Map<UUID, EquipamentoRef> equipById = equipIds.isEmpty()
        ? Map.of() : equipamentoRefRepository.findAllById(equipIds).stream().collect(Collectors.toMap(EquipamentoRef::getId, e -> e));

    Set<UUID> ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());
    Map<UUID, List<TicketParticipant>> participantsByTicketId = ticketIds.isEmpty()
        ? Map.of() : participantRepository.findByTicketIdIn(ticketIds).stream()
            .collect(Collectors.groupingBy(p -> p.getTicket().getId()));

    return tickets.stream()
        .map(t -> toResponse(
            t,
            t.getCategoriaId() == null ? null : categoriesById.get(t.getCategoriaId()),
            t.getSlaId() == null ? null : slaNamesById.get(t.getSlaId()),
            t.getSetorId() == null ? null : setorNamesById.get(t.getSetorId()),
            t.getEquipamentoRefId() == null ? null : equipById.get(t.getEquipamentoRefId()),
            participantsByTicketId.getOrDefault(t.getId(), List.of())))
        .toList();
  }

  private TicketResponse toResponse(Ticket ticket) {
    Category category = ticket.getCategoriaId() == null ? null : categoryRepository.findById(ticket.getCategoriaId()).orElse(null);
    String slaNome = ticket.getSlaId() == null ? null : slaRepository.findById(ticket.getSlaId()).map(Sla::getNome).orElse(null);
    String setorNome = ticket.getSetorId() == null ? null : setorRepository.findById(ticket.getSetorId()).map(Setor::getNome).orElse(null);
    EquipamentoRef equip = ticket.getEquipamentoRefId() == null ? null : equipamentoRefRepository.findById(ticket.getEquipamentoRefId()).orElse(null);
    List<TicketParticipant> participants = participantRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

    return toResponse(ticket, category, slaNome, setorNome, equip, participants);
  }

  /** 2026-09-08: {@code TicketResponse} não carrega mais anexos - TODO anexo agora está sempre
   *  vinculado a um comentário (o de abertura incluso, ver TicketCommentService#createOpeningComment),
   *  vem embutido em TicketCommentResponse#attachments. */
  private TicketResponse toResponse(
      Ticket ticket, Category category, String slaNome, String setorNome, EquipamentoRef equip,
      List<TicketParticipant> participants) {

    boolean estourado = ticket.getDataLimiteSla() != null
        && ticket.getDataLimiteSla().isBefore(Instant.now())
        && !TERMINAL_STATUSES.contains(ticket.getStatus());

    String currentUserId = currentUserProvider.getCurrentUser() == null ? null : currentUserProvider.getCurrentUser().userId();
    boolean aguardandoMinhaAcao = currentUserId != null && currentUserId.equals(ticket.getAguardandoAcaoDe());

    List<TicketParticipantResponse> participantResponses = toParticipantResponses(participants);

    return new TicketResponse(
        ticket.getId(),
        ticket.getNumero(),
        ticket.getTitulo(),
        ticket.getDescricao(),
        ticket.getStatus(),
        ticket.getPrioridade(),
        ticket.getCategoriaId(),
        category == null ? null : category.getNome(),
        ticket.getSlaId(),
        slaNome,
        ticket.getSetorId(),
        setorNome,
        ticket.getSolicitanteId(),
        resolveUserName(ticket.getSolicitanteId()),
        ticket.getResponsavelId(),
        resolveUserName(ticket.getResponsavelId()),
        ticket.getEquipamentoRefId(),
        equip == null ? null : equip.getCodigo(),
        equip == null ? null : equip.getNome(),
        ticket.getCanalOrigem(),
        ticket.getDataAbertura(),
        ticket.getDataLimiteSla(),
        estourado,
        ticket.getDataResolucao(),
        ticket.getDataFechamento(),
        aguardandoMinhaAcao,
        participantResponses,
        ticket.getCreatedAt(),
        ticket.getUpdatedAt());
  }

  private String resolveUserName(String userId) {
    if (userId == null) {
      return null;
    }
    return userDirectoryService.summaryFor(UserDirectoryService.parseIdOrNull(userId)).map(u -> u.name()).orElse(null);
  }

  /** Usado por TicketDashboardService pra reaproveitar o mesmo cálculo de "estourado"/nomes
   *  resolvidos (categoria/sla/setor/equipamento/solicitante/responsável) sem duplicar a lógica de
   *  toResponse. */
  List<TicketResponse> allAsResponses() {
    return toResponseList(ticketRepository.findAllByOrderByCreatedAtDesc());
  }
}
