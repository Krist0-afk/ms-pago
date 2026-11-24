package com.pedidos360.pago.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

// Registro de cada bloqueo preventivo por uso repetido de una tarjeta
@Entity
@Table(name = "alertas_fraude")
public class AlertaFraude {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String tarjetaHash;
    @Column(length = 4)
    private String ultimos4;
    private String usuarioEmail;
    private Long ordenId;
    private long intentos;
    private int ventanaSegundos;
    private Instant creadoEn;

    public AlertaFraude() {
    }

    public AlertaFraude(String tarjetaHash, String ultimos4, String usuarioEmail, Long ordenId,
                        long intentos, int ventanaSegundos) {
        this.tarjetaHash = tarjetaHash;
        this.ultimos4 = ultimos4;
        this.usuarioEmail = usuarioEmail;
        this.ordenId = ordenId;
        this.intentos = intentos;
        this.ventanaSegundos = ventanaSegundos;
        this.creadoEn = Instant.now();
    }

    public Long getId() { return id; }
    public String getUltimos4() { return ultimos4; }
    public String getUsuarioEmail() { return usuarioEmail; }
    public Long getOrdenId() { return ordenId; }
    public long getIntentos() { return intentos; }
    public int getVentanaSegundos() { return ventanaSegundos; }
    public Instant getCreadoEn() { return creadoEn; }
}
