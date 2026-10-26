package com.clinica.real.madrid.backend_citas.repository;

import com.clinica.real.madrid.backend_citas.model.DispositivoActivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DispositivoActivoRepository extends JpaRepository<DispositivoActivo, Long> {
    
    Optional<DispositivoActivo> findByFcmToken(String fcmToken);
    
    List<DispositivoActivo> findByUsuarioIdOrderByUltimaConexionDesc(Long usuarioId);
    
    void deleteByFcmToken(String fcmToken);

    void deleteByFcmTokenAndUsuarioId(String fcmToken, Long usuarioId);
    
    void deleteByUsuarioId(Long usuarioId);
}
