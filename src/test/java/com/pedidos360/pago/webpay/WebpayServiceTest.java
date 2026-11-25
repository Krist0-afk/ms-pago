package com.pedidos360.pago.webpay;

import com.pedidos360.pago.messaging.producer.PagoEventPublisher;
import com.pedidos360.pago.model.EstadoPago;
import com.pedidos360.pago.model.Pago;
import com.pedidos360.pago.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Flujo de Webpay con la base H2 y Transbank simulado
@SpringBootTest(properties = {"app.webpay.url-frontend=https://front.test",
        "app.cors.allowed-origins=https://front.test,https://otro-front.test"})
class WebpayServiceTest {

    @Autowired
    private WebpayService webpay;

    @Autowired
    private PagoRepository pagoRepository;

    @MockitoBean
    private WebpayCliente cliente;

    @MockitoBean
    private PagoEventPublisher publisher;

    @Autowired
    private com.pedidos360.pago.repository.AlertaFraudeRepository alertaRepository;

    @BeforeEach
    void preparar() {
        pagoRepository.deleteAll();
        alertaRepository.deleteAll();
        when(cliente.crear(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(new WebpayCliente.Creacion("tok-1", "https://webpay3gint.transbank.cl/webpayserver/initTransaction"));
    }

    private static WebpayCliente.Confirmacion confirmacion(String status, int codigo) {
        return new WebpayCliente.Confirmacion(status, codigo, 7990L, "P360-1-1", "1213", new WebpayCliente.Tarjeta("6623"));
    }

    @Test
    void iniciarRegistraPagoPendienteConElToken() {
        var inicio = webpay.iniciar(10L, 7990.0, "ana@duocuc.cl");

        Pago pago = pagoRepository.findById(inicio.pagoId()).orElseThrow();
        assertThat(inicio.token()).isEqualTo("tok-1");
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(pago.getMetodo()).isEqualTo("WEBPAY");
        assertThat(pago.getOrdenCompra()).startsWith("P360-10-");
        verify(cliente).crear(eq(pago.getOrdenCompra()), anyString(), eq(7990L), anyString());
        // Un pago pendiente todavía no se publica en Kafka
        verify(publisher, never()).publicar(any());
    }

    @Test
    void retornoAutorizadoApruebaPublicaYVuelveAlFront() {
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl");
        when(cliente.confirmar("tok-1")).thenReturn(confirmacion("AUTHORIZED", 0));

        String destino = webpay.retorno("tok-1", null, null);

        Pago pago = pagoRepository.findByWebpayToken("tok-1").orElseThrow();
        assertThat(destino).isEqualTo("https://front.test/ordenes?pago=aprobado&orden=10");
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pago.getUltimos4()).isEqualTo("6623");
        verify(publisher).publicar(any());
    }

    @Test
    void retornoRechazadoPorElBanco() {
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl");
        when(cliente.confirmar("tok-1")).thenReturn(confirmacion("FAILED", -1));

        String destino = webpay.retorno("tok-1", null, null);

        assertThat(destino).contains("pago=rechazado");
        assertThat(pagoRepository.findByWebpayToken("tok-1").orElseThrow().getEstado()).isEqualTo(EstadoPago.RECHAZADO);
    }

    @Test
    void clienteAnulaEnElFormularioDeWebpay() {
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl");

        String destino = webpay.retorno(null, "tok-1", "P360-10-1");

        assertThat(destino).contains("pago=anulado");
        assertThat(pagoRepository.findByWebpayToken("tok-1").orElseThrow().getMotivo()).isEqualTo("Pago anulado en Webpay");
        verify(cliente, never()).confirmar(anyString());
    }

    @Test
    void recargarLaPaginaDeRetornoNoConfirmaDosVeces() {
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl");
        when(cliente.confirmar("tok-1")).thenReturn(confirmacion("AUTHORIZED", 0));

        webpay.retorno("tok-1", null, null);
        String destino = webpay.retorno("tok-1", null, null);

        assertThat(destino).contains("pago=aprobado");
        verify(cliente, times(1)).confirmar("tok-1");
        verify(publisher, times(1)).publicar(any());
    }

    @Test
    void tokenDesconocidoVuelveAlFrontConError() {
        assertThat(webpay.retorno("no-existe", null, null)).isEqualTo("https://front.test/ordenes?pago=error");
    }

    @Test
    void cuartoWebpayEnDiezSegundosSeBloquea() {
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl");
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl");
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl");

        assertThatThrownBy(() -> webpay.iniciar(10L, 7990.0, "ana@duocuc.cl"))
                .isInstanceOf(com.pedidos360.pago.service.FraudeException.class);

        assertThat(pagoRepository.findAll()).filteredOn(p -> p.getEstado() == EstadoPago.BLOQUEADO_FRAUDE).hasSize(1);
        assertThat(alertaRepository.findAll()).hasSize(1);
        // El bloqueo se publica en Kafka (el dashboard lo muestra); los pendientes no
        verify(publisher, times(1)).publicar(any());
        // Otro usuario no se ve afectado
        assertThat(webpay.iniciar(11L, 7990.0, "otro@duocuc.cl").token()).isEqualTo("tok-1");
    }

    @Test
    void vuelveAlOrigenDesdeElQueSeInicioElPago() {
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl", "https://otro-front.test");
        when(cliente.confirmar("tok-1")).thenReturn(confirmacion("AUTHORIZED", 0));

        assertThat(webpay.retorno("tok-1", null, null)).startsWith("https://otro-front.test/ordenes?pago=aprobado");
    }

    @Test
    void ignoraUnOrigenQueNoEstaPermitido() {
        webpay.iniciar(10L, 7990.0, "ana@duocuc.cl", "https://sitio-malicioso.test");
        when(cliente.confirmar("tok-1")).thenReturn(confirmacion("AUTHORIZED", 0));

        assertThat(webpay.retorno("tok-1", null, null)).startsWith("https://front.test/ordenes?pago=aprobado");
    }
}
