package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.exception.ResourceNotFoundException;
import com.clinica.real.madrid.backend_citas.model.Disponibilidad;
import com.clinica.real.madrid.backend_citas.model.Medico;
import com.clinica.real.madrid.backend_citas.repository.DisponibilidadRepository;
import com.clinica.real.madrid.backend_citas.repository.MedicoRepository;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DisponibilidadService {

    @Autowired
    private DisponibilidadRepository disponibilidadRepository;

    @Autowired
    private MedicoRepository medicoRepository;

    @Autowired
    private com.clinica.real.madrid.backend_citas.repository.CitaRepository citaRepository;

    public List<Disponibilidad> listar() {
        return disponibilidadRepository.findAll();
    }

    public Disponibilidad obtenerPorId(Long id) {
        return disponibilidadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Disponibilidad no encontrada"));
    }

    public List<Disponibilidad> listarPorMedico(Long medicoId) {
        return disponibilidadRepository.findByMedicoId(medicoId);
    }

    public List<Disponibilidad> listarPorRango(Long medicoId, LocalDate inicio, LocalDate fin) {
        return disponibilidadRepository.findByMedicoIdAndFechaBetween(medicoId, inicio, fin);
    }

    @Transactional
    public List<Disponibilidad> guardarDisponibilidades(Long medicoId, List<Disponibilidad> disponibilidades) {
        Medico medico = medicoRepository.findById(medicoId)
                .orElseThrow(() -> new ResourceNotFoundException("Médico no encontrado"));

        for (Disponibilidad d : disponibilidades) {
            if (d.getId() != null) {
                Disponibilidad existente = disponibilidadRepository.findById(d.getId()).orElse(null);
                if (existente != null && existente.getMedico() != null && !existente.getMedico().getId().equals(medicoId)) {
                    throw new BadRequestException("El horario con ID " + d.getId() + " no pertenece a este medico");
                }
            } else if (d.getFecha() != null && d.getHoraInicio() != null) {
                disponibilidadRepository.findFirstByMedicoIdAndFechaAndHoraInicio(medicoId, d.getFecha(), d.getHoraInicio())
                        .ifPresent(existente -> d.setId(existente.getId()));
            }
            if (d.getEstado() == Disponibilidad.EstadoDisponibilidad.DISPONIBLE && d.getFecha() != null && d.getHoraInicio() != null) {
                LocalDateTime fechaHora = LocalDateTime.of(d.getFecha(), d.getHoraInicio());
                if (citaRepository.existsByMedicoIdAndFecha(medicoId, fechaHora)) {
                    d.setEstado(Disponibilidad.EstadoDisponibilidad.NO_DISPONIBLE);
                }
            }
            d.setMedico(medico);
            d.setFechaActualizacion(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        }

        return disponibilidadRepository.saveAll(disponibilidades);
    }

    @Transactional
    public void eliminarPorRango(Long medicoId, LocalDate inicio, LocalDate fin) {
        disponibilidadRepository.deleteByMedicoIdAndFechaBetween(medicoId, inicio, fin);
    }
}