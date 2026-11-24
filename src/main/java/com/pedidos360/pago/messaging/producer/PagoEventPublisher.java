package com.pedidos360.pago.messaging.producer;

import com.pedidos360.pago.dto.PagoEvent;
import com.pedidos360.pago.model.Pago;
import com.pedidos360.pago.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

// Productor Kafka de pagos. Cada pago queda marcado como pendiente hasta que Kafka confirma
// el envío; los pendientes se reintentan periódicamente, así ningún resultado se pierde
// aunque Kafka esté caído en el momento del pago.
@Component
public class PagoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PagoEventPublisher.class);

    private final KafkaTemplate<String, PagoEvent> kafkaTemplate;
    private final PagoRepository pagoRepository;
    private final String topico;

    public PagoEventPublisher(KafkaTemplate<String, PagoEvent> kafkaTemplate, PagoRepository pagoRepository,
                              @Value("${app.kafka.topicos.pagos}") String topico) {
        this.kafkaTemplate = kafkaTemplate;
        this.pagoRepository = pagoRepository;
        this.topico = topico;
    }

    public void publicar(Pago pago) {
        PagoEvent evento = PagoEvent.de(pago);
        try {
            kafkaTemplate.send(topico, evento.tarjetaHash(), evento).whenComplete((resultado, error) -> {
                if (error != null) {
                    log.warn("Pago #{} ({}) no llegó a Kafka, se reintentará: {}", pago.getId(), evento.estado(), error.getMessage());
                    return;
                }
                pagoRepository.findById(pago.getId()).ifPresent(p -> {
                    p.setEventoPublicado(true);
                    pagoRepository.save(p);
                });
                log.info("Pago #{} {} publicado en {}-{} offset {}", pago.getId(), evento.estado(), topico,
                        resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
            });
        } catch (RuntimeException e) {
            log.warn("Kafka no disponible para el pago #{}, se reintentará: {}", pago.getId(), e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.pagos.reintento-eventos-ms}", initialDelayString = "${app.pagos.reintento-eventos-ms}")
    public void reintentarPendientes() {
        var pendientes = pagoRepository.findTop50ByEventoPublicadoFalseAndCreadoEnBeforeOrderByCreadoEn(Instant.now().minusSeconds(10));
        if (!pendientes.isEmpty()) {
            log.info("Reintentando la publicación de {} pagos pendientes", pendientes.size());
            pendientes.forEach(this::publicar);
        }
    }
}
