# NimbusDesk — Spec do módulo de Chamados de TI (Fase 2)

> Este documento reconcilia o prompt original de criação do NimbusDesk (visão de produto: sistema de
> chamados de TI) com a stack e a arquitetura REAIS já em produção (base criada em 2026-09-07, ver
> `[[project_nimbusdesk_base_created]]` na memória). A Fase 1 (base: dashboard vazio, Segurança,
> Configurações) já está pronta. Esta é a Fase 2: o módulo de negócio.

## O que mudou em relação ao prompt original

| Prompt original | Realidade / decisão |
|---|---|
| Spring Boot 3.3.x | **Spring Boot 4.0.2** (já em uso) |
| Angular 18.x | **Angular 21.1.x** + PrimeNG (já em uso) |
| Monorepo `/backend /frontend` | **2 repos separados** (`NimbusDeskServer`, `NimbusDeskWeb`), padrão de todos os apps Nimbus |
| Pacotes hexagonais (`domain/application/infrastructure/api`) | **`com.nimbusdesk.tickets.{controller,core,dto/{request,response},model,repository}`** — mesmo estilo do `com.nimbusflow.tickets` (não hexagonal, "feature package") |
| "OAuth2 Resource Server mockado" | BFF real (sessão+cookie contra NimbusAuth, já implementado na Fase 1) |
| Papéis fixos no código (enum `SOLICITANTE`/`TECNICO_TI`/...) | **Grupos configuráveis no NimbusAuth**, mapeados a permissões granulares (ver seção Permissões) — não há enum de papel no código Java/TS |
| Testcontainers | Mantido como convenção — usar `nimbus-commons-server:testFixtures` se aplicável, senão Testcontainers Postgres puro |

## Pacotes (backend, `com.nimbusdesk.tickets`)

```
com.nimbusdesk.tickets/
├── controller/   TicketController, CategoryController, SlaController, SetorController,
│                 EquipamentoRefController (só leitura), TicketDashboardController
├── core/         TicketService, CategoryService, SlaService, SetorService,
│                 EquipamentoSyncService (job), TicketCommentService, TicketAttachmentService,
│                 TicketDashboardService
├── dto/
│   ├── request/  TicketRequest, TicketCloseRequest, TicketAssignRequest, TicketCommentRequest,
│   │             CategoryRequest, SlaRequest, SetorRequest
│   └── response/ TicketResponse, TicketCommentResponse, TicketAttachmentResponse,
│                 TicketStatusHistoryResponse, CategoryResponse, SlaResponse, SetorResponse,
│                 EquipamentoRefResponse, TicketDashboardResponse
├── model/        Ticket, TicketStatus, TicketPriority, TicketComment, TicketAttachment,
│                 TicketStatusHistory, Category, Sla, Setor, EquipamentoRef
└── repository/   (um por entidade acima)
```

Padrão "referência crua + FK entre módulos-raiz" (mesmo do NimbusFlow): `Ticket.equipamentoRefId`
é uma coluna UUID crua com FK de banco pra `equipamento_ref`, sem `@ManyToOne` — resolvido via
injeção direta do `EquipamentoRefRepository` no `TicketService` quando precisar exibir o
equipamento vinculado.

## Modelo de domínio (campos finais)

### Ticket (chamado)
`id, numero (sequencial, gerado), titulo, descricao, status, prioridade, categoriaId (FK crua →
category), slaId (derivado da categoria, não setado manualmente), setorId (FK crua → setor),
solicitanteId (String, raw id do NimbusAuth), responsavelId (String, nullable), equipamentoRefId
(UUID, FK crua → equipamento_ref, nullable), canalOrigem (enum: PORTAL, EMAIL, TELEFONE — só
PORTAL implementado nesta fase, os outros são placeholder pra futuro), dataAbertura, dataLimiteSla
(calculada na abertura a partir do SLA da categoria), dataResolucao, dataFechamento, createdAt,
updatedAt`.

`numero`: sequencial formatado (`CH-2026-00001`), gerado via `sequence` do Postgres — não usar
UUID como número visível pro usuário (segue convenção de "número de patrimônio" do módulo
Patrimônio).

### TicketStatus (enum)
`ABERTO, EM_ANDAMENTO, AGUARDANDO_SOLICITANTE, RESOLVIDO, FECHADO, CANCELADO`.

Transições permitidas (validação simples no `TicketService`, sem state machine formal — mesmo
padrão do NimbusFlow):
- `ABERTO → EM_ANDAMENTO` (ao assumir/atribuir responsável)
- `EM_ANDAMENTO → AGUARDANDO_SOLICITANTE` (técnico pede retorno do solicitante)
- `AGUARDANDO_SOLICITANTE → EM_ANDAMENTO` (solicitante respondeu, ou técnico retoma)
- `EM_ANDAMENTO / AGUARDANDO_SOLICITANTE → RESOLVIDO`
- `RESOLVIDO → FECHADO` (confirmação final, pode ser manual ou automática após N dias — job
  futuro, fora de escopo nesta fase, deixar `TODO` documentado)
- `RESOLVIDO → EM_ANDAMENTO` (reaberto pelo solicitante logo após resolução, antes de fechar)
- `FECHADO → EM_ANDAMENTO` ("Reaberto", conforme prompt original)
- `ABERTO / EM_ANDAMENTO → CANCELADO`

Toda transição grava uma linha em `TicketStatusHistory` (`ticketId, statusAnterior, statusNovo,
usuarioId, timestamp`) — **isso não existe no NimbusFlow** (lá o rastro é implícito), é construído
do zero aqui porque o prompt pede auditoria explícita.

### Category (categoria)
`id, nome, slaId (FK crua → sla), ativo`.

### Sla
`id, nome, tempoRespostaMin, tempoResolucaoMin`. `dataLimiteSla` do chamado =
`dataAbertura + tempoResolucaoMin` (minutos) da SLA da categoria escolhida. Sem escalonamento
automático nesta fase (fora de escopo, conforme prompt) — só o campo/indicador de "estourado"
(`dataLimiteSla < now() && status not in (RESOLVIDO, FECHADO, CANCELADO)`), calculado em leitura,
não persistido. Teve um campo `prioridadeDefault` até 2026-09-09, removido quando a prioridade
passou a ser sempre informada pelo solicitante na abertura do chamado (ver `TicketRequest#prioridade`).

### Setor
Mesmo desenho do `Department` do NimbusFlow: `id, nome, userIds (@ElementCollection de String,
raw ids do NimbusAuth), ativo`. Sem setor reservado tipo "Gestor" nesta fase (pode ser adicionado
depois se necessário).

### TicketComment
`id, ticketId (FK), autorId (String), mensagem, interno (bool), createdAt`. Nome do autor resolvido
em leitura via `UserDirectoryService.summaryFor()` (mesmo padrão do NimbusFlow) — nunca persistido.
Comentário interno só visível pra quem tem `CHAMADO_MANAGE` (staff); comentário público visível
pra todos que veem o chamado.

### TicketAttachment
`id, ticketId (FK), nomeArquivo, storageKey (MinIO), tipoMime, tamanhoBytes, createdAt`. Mesmo
padrão do `StorageService`/`S3StorageService` do NimbusFlow: upload grava a *key*, leitura gera URL
pré-assinada com TTL curto, nunca persiste URL.

### EquipamentoRef (cópia local, somente leitura pelo app — escrita só pelo job de sync)
`id (mesmo UUID do Equipamento no NimbusFlow — chave de correlação), codigo (numeroPatrimonio),
nome (descricao), localizacao (String livre, nullable — ver nota abaixo), statusOperacional
(mapeado do StatusEquipamento do NimbusFlow), atualizadoEm (updatedAt de origem), sincronizadoEm
(timestamp local do último upsert)`.

**Nota sobre localização**: o `EquipamentoResponse` do NimbusFlow hoje não inclui localização atual
(precisaria de join com `HistoricoLocalizacao`). Decisão: o endpoint novo de sync (ver abaixo) VAI
incluir a localização atual (é barato calcular do lado do NimbusFlow, que já tem os dados) — não
adiar essa resolução pro NimbusDesk.

## Integração com NimbusFlow — sincronização de equipamentos

**Endpoint novo no NimbusFlowServer** (não existe hoje, precisa ser criado):
```
GET /internal/v1/equipamentos?atualizadoApos={timestamp ISO-8601}
Header: X-Internal-Secret: <mesmo mecanismo do NimbusAuth, NIMBUS_INTERNAL_API_SECRET>
```
- Fora da BFF chain (`/bff/**`, sessão/cookie) — rota nova sob `/internal/**`, protegida por um
  filtro equivalente ao `InternalApiSecretFilter` do NimbusAuthServer (replicado no
  NimbusFlowServer, já que não existe lá hoje).
- Resposta: lista de `EquipamentoResponse` ampliado com `localizacaoAtual` (join com
  `HistoricoLocalizacao` status=ATIVO), filtrada em memória por `updatedAt > atualizadoApos`
  (volume de 62 registros — sem necessidade de paginação real ou índice dedicado nesta fase).
- Sem paginação nesta fase (mesmo raciocínio de volume pequeno já usado em todo o resto do
  Patrimônio).

**Job no NimbusDeskServer** (`com.nimbusdesk.tickets.core.EquipamentoSyncService`):
- `@Scheduled` a cada 20 minutos (dev: configurável via `nimbus.equipamento-sync.interval`).
- Guarda o timestamp da última execução bem-sucedida numa tabela simples
  (`equipamento_sync_state`, linha única) — não usar o `updatedAt` mais recente já sincronizado
  como cursor (evita perder registros em caso de clock skew; usa o timestamp de INÍCIO da chamada
  anterior, com uma margem de segurança de alguns minutos).
- Client HTTP (`NimbusFlowInternalClient`) chama o endpoint acima com o secret compartilhado
  (env var `NIMBUS_FLOW_INTERNAL_BASE_URL` + `NIMBUS_FLOW_INTERNAL_SECRET`).
- **Resiliente**: falha de rede/timeout/4xx/5xx é logada (log + métrica simples via contador em
  memória, sem Micrometer novo) e NÃO propaga exceção pro scheduler nem derruba o boot — o sistema
  continua com a última cópia local. Job tenta de novo na próxima janela.
- Upsert em `EquipamentoRef` por `id`.

## Permissões (seed novo no NimbusAuth, app_key='nimbusdesk')

Nomes exatos (mesmo padrão CONSULT/MANAGE já usado em todo o Nimbus):

| Permissão | Uso |
|---|---|
| `CHAMADO_CONSULT` | ver TODOS os chamados (não só os próprios) — telas de lista completa, dashboard |
| `CHAMADO_MANAGE` | mudar status, atribuir responsável, comentário interno |
| `CATEGORIA_CONSULT` / `CATEGORIA_MANAGE` | tela Configurações > Categorias |
| `SLA_CONSULT` / `SLA_MANAGE` | tela Configurações > SLAs |
| `SETOR_TI_CONSULT` / `SETOR_TI_MANAGE` | tela Configurações > Setores (nome com sufixo `_TI` pra não colidir com um futuro "Setor" de outro domínio) |
| `EQUIPAMENTO_REF_CONSULT` | ver equipamento vinculado ao chamado (sem MANAGE — é só leitura, escrito pelo job) |

**Sem gate de permissão** (qualquer usuário autenticado): abrir chamado, comentar (público) num
chamado que já pode ver, ver "meus chamados" (`GET /bff/v1/tickets/mine` — filtrado
server-side por `solicitanteId = usuário atual OR responsavelId = usuário atual`, não precisa de
`CHAMADO_CONSULT`). Isso é o que viabiliza o papel `SOLICITANTE` sem lhe dar acesso a chamados de
terceiros.

**Grupos novos a seedar** (além de `SUPPORT`/`ADMINISTRADOR`, que já existem e recebem tudo):
- `SOLICITANTE` — nenhuma permissão de negócio (usa só as rotas sem gate acima).
- `TECNICO_TI` — `CHAMADO_CONSULT`, `CHAMADO_MANAGE`, `EQUIPAMENTO_REF_CONSULT`.
- `GESTOR_TI` — mesmo que `TECNICO_TI` + `CATEGORIA_CONSULT`/`SLA_CONSULT`/`SETOR_TI_CONSULT`
  (só consulta de configuração, não gerencia).
- `ADMIN_HELPDESK` — tudo (equivalente a um subconjunto do ADMINISTRADOR, mas escopado só a
  negócio, sem Segurança/Backup — mesmo espírito do grupo "Administração" que o NimbusFlow tem).

## Frontend (NimbusDeskWeb)

Novo módulo `features/tickets/` (mesma estrutura de `features/security`/`features/settings` já
existentes): `tickets-list` (com toggle "Meus chamados" / "Todos", só mostra o toggle "Todos" se
`hasSupportOr(CHAMADO.VIEW)`), `ticket-detail` (tela dedicada, NÃO diálogo — timeline de
comentários + histórico de status exige mais espaço que um dialog, diferente do padrão do
NimbusFlow), `tickets-create-dialog`. Menu: item "Chamados" raiz (ícone `pi-ticket`), e
Configurações ganha 3 novos itens (Categorias/SLAs/Setores). Dashboard (hoje vazio) ganha
indicadores reais: chamados por status (donut), SLA estourado (contador + lista), chamados por
categoria (barra) — usa Chart.js via `primeng/chart`, mesmo padrão do `DashboardComponent` do
NimbusFlow.

## Fora de escopo desta fase (igual ao prompt original)

Notificações por e-mail/WhatsApp, escalonamento automático de SLA, portal externo pra
solicitantes fora da organização, fechamento automático de `RESOLVIDO→FECHADO` após N dias
(deixar `TODO` no código).
