package com.clinica.real.madrid.backend_citas.dto;

import java.time.LocalDate;

public class UsuarioRegistroRequest {

    private String nombre;
    private String apellido;
    private String correo;
    private String contrasena;
    private String telefono;
    private LocalDate fechaNacimiento;
    private String dni;
    private Long paisId;
    private String configuracionVisual;
    private String recaptchaToken;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public String getDni() { return dni; }
    public void setDni(String dni) { this.dni = dni; }

    public Long getPaisId() { return paisId; }
    public void setPaisId(Long paisId) { this.paisId = paisId; }

    public String getConfiguracionVisual() { return configuracionVisual; }
    public void setConfiguracionVisual(String configuracionVisual) { this.configuracionVisual = configuracionVisual; }

    public String getRecaptchaToken() { return recaptchaToken; }
    public void setRecaptchaToken(String recaptchaToken) { this.recaptchaToken = recaptchaToken; }
}
