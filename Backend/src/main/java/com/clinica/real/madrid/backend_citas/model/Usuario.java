package com.clinica.real.madrid.backend_citas.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, unique = true, length = 100)
    private String correo;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false, length = 255)
    private String contrasena;

    @Column(nullable = true, unique = true, length = 20)
    private String dni;

    @Column(nullable = true, length = 20)
    private String telefono;

    @Column(name = "fecha_nacimiento", nullable = true)
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoUsuario estado = EstadoUsuario.ACTIVADO;

    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pais_id", nullable = true)
    private Pais pais;

    @ManyToMany
    @JoinTable(
        name = "medico_especialidades",
        joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "especialidad_id")
    )
    private Set<Especialidad> especialidades;

    @JsonIgnore
    @Column(name = "token_recuperacion")
    private String tokenRecuperacion;

    @JsonIgnore
    @Column(name = "token_expira")
    private LocalDateTime tokenExpira;

    @Column(name = "configuracion_visual", length = 50)
    private String configuracionVisual = "NINGUNO";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    @JsonIgnore
    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }

    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public EstadoUsuario getEstado() { return estado; }
    public void setEstado(EstadoUsuario estado) { this.estado = estado; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public Pais getPais() { return pais; }
    public void setPais(Pais pais) { this.pais = pais; }

    public Set<Especialidad> getEspecialidades() { return especialidades; }
    public void setEspecialidades(Set<Especialidad> especialidades) {this.especialidades = especialidades; }
	public String getTokenRecuperacion() {
		return tokenRecuperacion;
	}
	public void setTokenRecuperacion(String tokenRecuperacion) {
		this.tokenRecuperacion = tokenRecuperacion;
	}
	public LocalDateTime getTokenExpira() {
		return tokenExpira;
	}
	public void setTokenExpira(LocalDateTime tokenExpira) {
		this.tokenExpira = tokenExpira;
	}

    @Column(name = "foto_url", length = 255)
    private String fotoUrl;

    public String getConfiguracionVisual() { return configuracionVisual; }
    public void setConfiguracionVisual(String configuracionVisual) { this.configuracionVisual = configuracionVisual; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    @Column(name = "recibir_notificaciones_push", columnDefinition = "boolean default true")
    private Boolean recibirNotificacionesPush = true;

    public Boolean getRecibirNotificacionesPush() { return recibirNotificacionesPush; }
    public void setRecibirNotificacionesPush(Boolean recibirNotificacionesPush) { this.recibirNotificacionesPush = recibirNotificacionesPush; }
}
