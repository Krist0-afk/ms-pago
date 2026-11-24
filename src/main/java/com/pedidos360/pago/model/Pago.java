package com.pedidos360.pago.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "pagos", indexes = @Index(name = "idx_pago_tarjeta_fecha", columnList = "tarjetaHash,creadoEn"))
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ordenId;
    private String usuarioEmail;
    private Double monto;

    // Solo el hash y los últimos 4 dígitos: el número de la tarjeta nunca se guarda
    @Column(length = 64)
    private String tarjetaHash;
    @Column(length = 4)
    private String ultimos4;

    @Enumerated(EnumType.STRING)
    private EstadoPago estado;
    private String motivo;
    private Instant creadoEn;

    // false mientras el evento no se haya confirmado en Kafka (se reintenta)
    private boolean eventoPublicado;

    public Pago() {
    }

    public Pago(Long ordenId, String usuarioEmail, Double monto, String tarjetaHash, String ultimos4, Instant creadoEn) {
        this.ordenId = ordenId;
        this.usuarioEmail = usuarioEmail;
        this.monto = monto;
        this.tarjetaHash = tarjetaHash;
        this.ultimos4 = ultimos4;
        this.creadoEn = creadoEn;
    }

    public void resolver(EstadoPago estado, String motivo) {
        this.estado = estado;
        this.motivo = motivo;
    }

    public Long getId() { return id; }
    public Long getOrdenId() { return ordenId; }
    public String getUsuarioEmail() { return usuarioEmail; }
    public Double getMonto() { return monto; }
    public String getTarjetaHash() { return tarjetaHash; }
    public String getUltimos4() { return ultimos4; }
    public EstadoPago getEstado() { return estado; }
    public String getMotivo() { return motivo; }
    public Instant getCreadoEn() { return creadoEn; }
    public boolean isEventoPublicado() { return eventoPublicado; }
    public void setEventoPublicado(boolean eventoPublicado) { this.eventoPublicado = eventoPublicado; }
}
