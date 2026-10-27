package com.clinica.real.madrid.backend_citas.repository;

import com.clinica.real.madrid.backend_citas.model.Cita;
import com.clinica.real.madrid.backend_citas.model.EstadoCita;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface CitaRepository extends JpaRepository<Cita, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cita c WHERE c.id = :id")
    java.util.Optional<Cita> findByIdForUpdate(@Param("id") Long id);

    List<Cita> findByPacienteIdAndMedico_Usuario_IdOrderByFechaDesc(Long pacienteId, Long usuarioMedicoId);

    @Query("SELECT c.id FROM Cita c WHERE c.estado = :estado AND c.fechaCreacion < :limite")
    List<Long> findIdsAbandonadas(@Param("estado") EstadoCita estado, @Param("limite") LocalDateTime limite);

    boolean existsByPacienteId(Long pacienteId);

    boolean existsByMedicoId(Long medicoId);

    boolean existsByMedicoIdAndFecha(Long medicoId, LocalDateTime fecha);

    List<Cita> findByPacienteIdOrderByFechaDesc(Long pacienteId);

    List<Cita> findByMedicoIdOrderByFechaDesc(Long medicoId);

    @Query("SELECT c FROM Cita c " +
           "JOIN FETCH c.medico m " +
           "JOIN FETCH m.usuario u " +
           "JOIN FETCH m.especialidad e " +
           "WHERE c.paciente.id = :pacienteId " +
           "ORDER BY c.fecha DESC")
    List<Cita> findByPacienteIdWithMedicoAndEspecialidad(@Param("pacienteId") Long pacienteId);

    List<Cita> findAllByOrderByFechaDesc();

    @Modifying
    @Transactional
    @Query("DELETE FROM Cita c WHERE c.paciente.id = :pacienteId")
    void deleteByPacienteId(@Param("pacienteId") Long pacienteId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Cita c WHERE c.medico.id = :medicoId")
    void deleteByMedicoId(@Param("medicoId") Long medicoId);

    @Modifying
    @Query("UPDATE Cita c SET c.estado = :nuevoEstado WHERE c.id = :id")
    int actualizarEstado(@Param("id") Long id, @Param("nuevoEstado") EstadoCita nuevoEstado);

    List<Cita> findByFechaBetweenAndEstado(LocalDateTime desde, LocalDateTime hasta, EstadoCita estado);

    List<Cita> findByEstadoOrderByFechaDesc(EstadoCita estado);

    List<Cita> findByEstadoAndFechaCreacionBefore(EstadoCita estado, LocalDateTime limite);

    List<Cita> findByPacienteIdAndEstadoOrderByFechaDesc(Long pacienteId, EstadoCita estado);
}
