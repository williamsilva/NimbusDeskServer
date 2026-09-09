package com.nimbusdesk.tickets.core;

import com.nimbusdesk.tickets.model.Ticket;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Broadcast em memória (mesmo processo, sem broker externo) de "algo mudou num chamado" via
 * Server-Sent Events - {@code GET /bff/v1/tickets/events} (ver TicketController#events). Objetivo
 * (2026-09-08): telas de listagem/detalhe se atualizam sozinhas quando um chamado é criado ou
 * modificado, sem o usuário precisar clicar em "Atualizar" - ver TicketService#create/update/
 * assign/changeStatus/replaceParticipants e TicketCommentService#add, que chamam
 * {@link #broadcastCreated}/{@link #broadcastUpdated} no fim de cada escrita bem-sucedida.
 *
 * <p>Payload PROPOSITALMENTE mínimo ({@code id}/{@code numero}/{@code type}, nada de dado
 * sensível) - o evento só avisa "algo mudou", quem decide o quê mostrar e se pode ver continua
 * sendo os endpoints normais (GET/search), que já reforçam visibilidade por usuário. Por isso o
 * broadcast é GLOBAL (todo cliente conectado recebe todo evento) de propósito: simplifica bastante
 * (não precisa calcular quem pode ver cada chamado na hora do broadcast) e é seguro porque o
 * payload não vaza nada que já não seja público entre usuários autenticados (um número de chamado
 * não é informação sensível).
 *
 * <p>Em memória, por instância - não sincroniza entre múltiplas réplicas do servidor (não é o caso
 * hoje, single-instance em dev/produção; se algum dia escalar horizontalmente, precisaria de um
 * broker real, ex. Redis pub/sub, pra os eventos de uma réplica chegarem aos clientes conectados
 * em outra). Escopo: só o módulo de Chamados por enquanto - primeiro uso de SSE no ecossistema
 * Nimbus (não havia precedente em nenhum outro produto, ver PROJECT_SPEC.md).
 */
@Slf4j
@Component
public class TicketEventBroadcaster {

  /** Timeout bem longo (30min) - o navegador (EventSource nativo) reconecta sozinho se a conexão
   *  cair antes disso; não queremos que o Spring feche a conexão no meio de uma sessão normal de
   *  uso só por timeout. */
  private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

  private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  /** {@code GET /bff/v1/tickets/events} chama isto pra registrar a conexão (ver TicketController).
   *  Remove o emitter da lista sozinho em qualquer desfecho (completou/deu timeout/deu erro) - sem
   *  isso a lista cresceria indefinidamente a cada reconexão do navegador. */
  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(e -> emitters.remove(emitter));

    try {
      // Evento inicial - confirma a conexão pro EventSource do frontend logo de cara (senão o
      // "open" só dispararia no primeiro evento de verdade, que pode demorar minutos).
      emitter.send(SseEmitter.event().name("connected").data("ok"));
    } catch (IOException e) {
      emitters.remove(emitter);
    }
    return emitter;
  }

  public void broadcastCreated(Ticket ticket) {
    broadcast("created", ticket);
  }

  public void broadcastUpdated(Ticket ticket) {
    broadcast("updated", ticket);
  }

  private void broadcast(String type, Ticket ticket) {
    if (emitters.isEmpty()) {
      return;
    }
    TicketEvent payload = new TicketEvent(ticket.getId(), ticket.getNumero(), type);
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name("ticket").data(payload));
      } catch (IOException | IllegalStateException e) {
        emitters.remove(emitter);
      }
    }
  }

  /** Comentário SSE (":\n\n", sem nome de evento - o EventSource do navegador ignora, mas mantém a
   *  conexão viva através de proxies/load balancers que fecham conexões ociosas) a cada 20s. */
  @Scheduled(fixedRate = 20_000)
  void heartbeat() {
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().comment("ping"));
      } catch (IOException | IllegalStateException e) {
        emitters.remove(emitter);
      }
    }
  }

  public record TicketEvent(UUID id, String numero, String type) {
  }
}
