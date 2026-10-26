package com.clinica.real.madrid.backend_citas.repository;

import com.clinica.real.madrid.backend_citas.model.SolicitudEmpleo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SolicitudEmpleoRepository extends JpaRepository<SolicitudEmpleo, Long> {
    List<SolicitudEmpleo> findByEstado(SolicitudEmpleo.EstadoSolicitud estado);
    boolean existsByCorreoAndEstado(String correo, SolicitudEmpleo.EstadoSolicitud estado);
    boolean existsByDniAndEstado(String dni, SolicitudEmpleo.EstadoSolicitud estado);
}
