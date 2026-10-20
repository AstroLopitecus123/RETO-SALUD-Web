package com.clinica.real.madrid.backend_citas.exception;

public class HorarioNoDisponibleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HorarioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
