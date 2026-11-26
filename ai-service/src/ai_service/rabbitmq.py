import asyncio
import logging

import aio_pika
from aio_pika import ExchangeType, IncomingMessage

from ai_service.analysis import AnalysisRequest, Analyzer
from ai_service.config import Settings
from ai_service.messaging import (
    ProcessarRedacaoCommand,
    RedacaoAnalisadaEvent,
)

log = logging.getLogger(__name__)


class RabbitMQClient:

    def __init__(self, settings: Settings, analyzer: Analyzer | None):
        self.settings = settings
        self.analyzer = analyzer
        self.connection = None
        self.channel = None
        self.exchange = None

    async def connect(self):
        self.connection = await aio_pika.connect_robust(
            self.settings.rabbitmq_url
        )

        self.channel = await self.connection.channel()
        await self.channel.set_qos(prefetch_count=1)

        self.exchange = await self.channel.declare_exchange(
            self.settings.rabbitmq_exchange,
            ExchangeType.DIRECT,
            durable=True,
        )

        queue = await self.channel.declare_queue(
            self.settings.rabbitmq_queue,
            durable=True,
        )

        await queue.bind(
            self.exchange,
            routing_key=self.settings.rabbitmq_queue,
        )

        log.info("AI Service conectado ao RabbitMQ.")

        if self.analyzer is not None:
            await queue.consume(self.process_message)
            log.info("Consumidor de redações iniciado.")
        else:
            log.warning(
                "Consumidor não iniciado: Analyzer indisponível."
            )

    async def process_message(self, message: IncomingMessage):
        async with message.process():
            try:
                data = ProcessarRedacaoCommand.model_validate_json(
                    message.body
                )

                request = AnalysisRequest(
                    studentId=data.studentId,
                    essayId=data.essayId,
                    theme=data.theme,
                    text=data.text,
                )

                result = await asyncio.to_thread(
                    self.analyzer.analyze,
                    request,
                )

                event = RedacaoAnalisadaEvent(
                    sagaId=data.sagaId,
                    requestId=data.requestId,
                    studentId=data.studentId,
                    essayId=data.essayId,
                    competencies=result.competencies,
                )

                await self.exchange.publish(
                    aio_pika.Message(
                        body=event.model_dump_json().encode(),
                        content_type="application/json",
                        delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                    ),
                    routing_key="saga.redacao.analisada",
                )

                log.info(
                    "Redação %s analisada com sucesso.",
                    data.essayId,
                )

            except Exception:
                log.exception(
                    "Erro ao processar redação recebida pelo RabbitMQ."
                )
                raise

    async def close(self):
        if self.connection and not self.connection.is_closed:
            await self.connection.close()
            log.info("Conexão RabbitMQ encerrada.")
