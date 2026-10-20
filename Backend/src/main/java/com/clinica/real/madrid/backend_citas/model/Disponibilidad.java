package com.clinica.real.madrid.backend_citas.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "disponibilidades", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_disp_bloque", columnNames = {"medico_id", "fecha", "hora_inicio"})
    },
    indexes = {
        @Index(name = "idx_disp_medico_fecha_estado", columnList = "medico_id, fecha, estado")
    }
)
public class Disponibilidad {

    public enum EstadoDisponibilidad {
        DISPONIBLE,
        NO_DISPONIBLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoDisponibilidad estado = EstadoDisponibilidad.DISPONIBLE;

    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now(java.time.ZoneId.systemDefault());

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion = LocalDateTime.now(java.time.ZoneId.systemDefault());

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public EstadoDisponibilidad getEstado() {
        return estado;
    }

    public void setEstado(EstadoDisponibilidad estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public Boolean getDisponible() {
        return this.estado == EstadoDisponibilidad.DISPONIBLE;
    }

    public void setDisponible(Boolean disponible) {
        this.estado = disponible ? EstadoDisponibilidad.DISPONIBLE : EstadoDisponibilidad.NO_DISPONIBLE;
    }
}

