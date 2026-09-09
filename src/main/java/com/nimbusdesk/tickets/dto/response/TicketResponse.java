package com.nimbusdesk.tickets.dto.response;

import com.nimbusdesk.tickets.model.TicketChannel;
import com.nimbusdesk.tickets.model.TicketPriority;
import com.nimbusdesk.tickets.model.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
    UUID id,
    String numero,
    String titulo,
    String descricao,
    TicketStatus status,
    TicketPriority prioridade,
    UUID categoriaId,
    String categoriaNome,
    UUID slaId,
    String slaNome,
    UUID setorId,
    String setorNome,
    String solicitanteId,
    String solicitanteNome,
    String responsavelId,
    String responsavelNome,
    UUID equipamentoRefId,
    String equipamentoRefCodigo,
    String equipamentoRefNome,
    TicketChannel canalOrigem,
    Instant dataAbertura,
    Instant dataLimiteSla,
    /** Calculado em leitura (dataLimiteSla &lt; now &amp;&amp; status ainda ativo) - nunca
     *  persistido, ver Ticket#dataLimiteSla. */
    boolean slaEstourado,
    Instant dataResolucao,
    Instant dataFechamento,
    /** Calculado em leitura ({@code aguardandoAcaoDe == usuário atual}) - alimenta o escopo
     *  "Aguardando minha ação" da lista (ver TicketService#search) e pode ser usado como badge na
     *  própria linha/detalhe. Nunca confundir com {@code slaEstourado}: são independentes. */
    boolean aguardandoMinhaAcao,
    /** Embutido - "compartilhado com" (ver TicketParticipantResponse), gerenciado via
     *  {@code PUT .../participants} (replace-all, ver TicketService#replaceParticipants).
     *  2026-09-08: não existe mais {@code attachments} aqui - TODO anexo do chamado está sempre
     *  vinculado a um comentário (o de abertura incluso, ver
     *  TicketCommentService#createOpeningComment), vem embutido em
     *  TicketCommentResponse#attachments, nunca duplicado neste response. */
    List<TicketParticipantResponse> participantes,
    Instant createdAt,
    Instant updatedAt) {
}
