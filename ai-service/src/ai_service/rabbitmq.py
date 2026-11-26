"""Participação do AI Service na SAGA de correção.

Consome ``ProcessarRedacaoCommand`` da fila ``ai.processar.redacao`` e sempre responde ao
orquestrador em ``saga.redacao.analisada``: com as notas (``status=OK``) ou com o motivo da
falha (``status=FAILED``), para que a SAGA execute as compensações. Uma mensagem que não pode
ser lida é descartada com log, sem travar a fila.
"""

import asyncio
import logging
from typing import Awaitable, Callable

from pydantic import ValidationError

from ai_service.analysis import AnalysisRequest, Analyzer
from ai_service.config import Settings
from ai_service.messaging import ProcessarRedacaoCommand, RedacaoAnalisadaEvent

log = logging.getLogger(__name__)

Publish = Callable[[bytes], Awaitable[None]]


async def handle_command(body: bytes, analyzer: Analyzer | None, publish: Publish) -> RedacaoAnalisadaEvent | None:
    try:
        command = ProcessarRedacaoCommand.model_validate_json(body)
    except ValidationError as error:
        log.error("Comando de análise inválido descartado: %s", error.errors()[:3])
        return None

    base = dict(sagaId=command.sagaId, requestId=command.requestId,
                studentId=command.studentId, essayId=command.essayId)
    if analyzer is None:
        event = RedacaoAnalisadaEvent(**base, status="FAILED",
                                      error="A chave do provedor de IA não está configurada no AI Service.")
    else:
        try:
            result = await asyncio.to_thread(analyzer.analyze, AnalysisRequest(
                studentId=command.studentId, essayId=command.essayId, theme=command.theme, text=command.text))
            event = RedacaoAnalisadaEvent(**base, status="OK", competencies=result.competencies)
        except Exception as error:
            log.warning("Falha ao analisar a redação %s (SAGA %s): %s", command.essayId, command.sagaId, error)
            event = RedacaoAnalisadaEvent(**base, status="FAILED", error=f"{type(error).__name__}: {str(error)[:300]}")

    await publish(event.model_dump_json().encode("utf-8"))
    log.info("SAGA %s: análise da redação %s respondida com status %s.", command.sagaId, command.essayId, event.status)
    return event


class RabbitMQClient:

    def __init__(self, settings: Settings, analyzer: Analyzer | None, retry_seconds: float = 5.0):
        self.settings = settings
        self.analyzer = analyzer
        self.retry_seconds = retry_seconds
        self.connection = None
        self.connected = False
        self._task: asyncio.Task | None = None

    def start(self) -> None:
        """Conecta em segundo plano: o AI Service sobe mesmo se o RabbitMQ ainda não estiver pronto."""
        self._task = asyncio.create_task(self._run())

    async def _run(self) -> None:
        import aio_pika

        while True:
            try:
                self.connection = await aio_pika.connect_robust(self.settings.rabbitmq_url)
                break
            except asyncio.CancelledError:
                raise
            except Exception as error:
                log.warning("RabbitMQ indisponível (%s); nova tentativa em %.0f s.", error, self.retry_seconds)
                await asyncio.sleep(self.retry_seconds)

        channel = await self.connection.channel()
        await channel.set_qos(prefetch_count=1)
        exchange = await channel.declare_exchange(self.settings.rabbitmq_exchange, aio_pika.ExchangeType.DIRECT,
                                                  durable=True)
        queue = await channel.declare_queue(self.settings.rabbitmq_queue, durable=True)
        await queue.bind(exchange, routing_key=self.settings.rabbitmq_queue)

        async def publish(body: bytes) -> None:
            await exchange.publish(
                aio_pika.Message(body=body, content_type="application/json",
                                 delivery_mode=aio_pika.DeliveryMode.PERSISTENT),
                routing_key=self.settings.rabbitmq_reply_routing_key,
            )

        async def on_message(message) -> None:
            # ack depois de responder; se a resposta não puder ser publicada, a mensagem volta para a fila.
            async with message.process(requeue=True):
                await handle_command(message.body, self.analyzer, publish)

        await queue.consume(on_message)
        self.connected = True
        log.info("AI Service conectado ao RabbitMQ, consumindo %s.", self.settings.rabbitmq_queue)

    async def close(self) -> None:
        if self._task and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except (asyncio.CancelledError, Exception):
                pass
        if self.connection and not self.connection.is_closed:
            await self.connection.close()
            log.info("Conexão RabbitMQ encerrada.")
