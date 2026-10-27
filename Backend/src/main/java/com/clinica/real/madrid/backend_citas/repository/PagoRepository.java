package com.clinica.real.madrid.backend_citas.repository;

import com.clinica.real.madrid.backend_citas.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findByCitaId(Long citaId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pago p WHERE p.cita.id = :citaId")
    List<Pago> findByCitaIdForUpdate(@Param("citaId") Long citaId);
    List<Pago> findByUsuarioId(Long usuarioId);
    List<Pago> findByCita_Medico_Id(Long medicoId);
    boolean existsByTransaccionId(String transaccionId);

    @Query("SELECT p.cita.id FROM Pago p WHERE p.id = :id")
    java.util.Optional<Long> findCitaIdByPagoId(@Param("id") Long id);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM Pago p WHERE p.usuario.id = :usuarioId")
    void deleteByUsuarioId(@Param("usuarioId") Long usuarioId);
}
