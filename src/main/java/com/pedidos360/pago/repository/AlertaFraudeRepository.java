package com.pedidos360.pago.repository;

import com.pedidos360.pago.model.AlertaFraude;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaFraudeRepository extends JpaRepository<AlertaFraude, Long> {

    List<AlertaFraude> findTop100ByOrderByCreadoEnDesc();
}
