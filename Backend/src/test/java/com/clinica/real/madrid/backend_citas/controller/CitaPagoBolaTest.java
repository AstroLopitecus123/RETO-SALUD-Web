package com.clinica.real.madrid.backend_citas.controller;

import com.clinica.real.madrid.backend_citas.dto.*;
import com.clinica.real.madrid.backend_citas.model.*;
import com.clinica.real.madrid.backend_citas.security.UsuarioPrincipal;
import com.clinica.real.madrid.backend_citas.service.CitaService;
import com.clinica.real.madrid.backend_citas.service.PagoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitaPagoBolaTest {
    @Mock CitaService citas;
    @Mock PagoService pagos;
    @InjectMocks CitaController citaController;
    @InjectMocks PagoController pagoController;

    private Authentication auth(Long id, String rol) {
        var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + rol));
        var principal = new UsuarioPrincipal(id, "test@example.invalid", "unused", autoridades);
        return new UsernamePasswordAuthenticationToken(principal, null, autoridades);
    }

    private Cita citaDeOtroMedico() {
        Usuario medicoUsuario = new Usuario();
        medicoUsuario.setId(99L);
        Medico medico = new Medico();
        medico.setId(9L);
        medico.setUsuario(medicoUsuario);
        Usuario paciente = new Usuario();
        paciente.setId(2L);
        Cita cita = new Cita();
        cita.setMedico(medico);
        cita.setPaciente(paciente);
        return cita;
    }

    @Test
    void medicoListaSoloSusCitasDelPaciente() {
        when(citas.obtenerCitasPorPacienteYMedico(2L, 7L)).thenReturn(List.of());
        assertEquals(200, citaController.listarCitasPorUsuario(2L, auth(7L, "MEDICO")).getStatusCode().value());
        verify(citas).obtenerCitasPorPacienteYMedico(2L, 7L);
        verify(citas, never()).obtenerCitasPorPaciente(anyLong());
    }

    @Test
    void medicoNoListaAgendaNiPagosDeOtroMedico() {
        when(citas.esMedicoPropio(9L, 7L)).thenReturn(false);
        assertEquals(403, citaController.listarCitasPorMedico(9L, auth(7L, "MEDICO")).getStatusCode().value());
        assertEquals(403, pagoController.obtenerPagosPorMedico(9L, auth(7L, "MEDICO")).getStatusCode().value());
        verify(citas, never()).obtenerCitasPorMedico(anyLong());
        verifyNoInteractions(pagos);
    }

    @Test
    void medicoPropioPuedeListarAgendaYPagos() {
        when(citas.esMedicoPropio(9L, 7L)).thenReturn(true);
        assertEquals(200, citaController.listarCitasPorMedico(9L, auth(7L, "MEDICO")).getStatusCode().value());
        assertEquals(200, pagoController.obtenerPagosPorMedico(9L, auth(7L, "MEDICO")).getStatusCode().value());
        verify(citas).obtenerCitasPorMedico(9L);
        verify(pagos).obtenerPagosPorMedico(9L);
    }

    @Test
    void medicoNoApruebaNiRechazaSolicitudAjena() {
        when(citas.obtenerPorId(1L)).thenReturn(citaDeOtroMedico());
        assertEquals(403, citaController.confirmarReprogramar(1L, auth(7L, "MEDICO")).getStatusCode().value());
        assertEquals(403, citaController.rechazarReprogramar(1L, auth(7L, "MEDICO")).getStatusCode().value());
        verify(citas, never()).confirmarReprogramacion(anyLong());
        verify(citas, never()).rechazarReprogramacion(anyLong());
    }

    @Test
    void medicoPropioPuedeAprobarYRechazar() {
        Cita cita = citaDeOtroMedico();
        cita.getMedico().getUsuario().setId(7L);
        when(citas.obtenerPorId(1L)).thenReturn(cita);
        assertEquals(200, citaController.confirmarReprogramar(1L, auth(7L, "MEDICO")).getStatusCode().value());
        assertEquals(200, citaController.rechazarReprogramar(1L, auth(7L, "MEDICO")).getStatusCode().value());
        verify(citas).confirmarReprogramacion(1L);
        verify(citas).rechazarReprogramacion(1L);
    }

    @Test
    void medicoNoLeePagosDeCitaAjenaAunqueNoTengaPagos() {
        when(citas.obtenerPorId(1L)).thenReturn(citaDeOtroMedico());
        assertEquals(403, pagoController.obtenerPagosPorCita(1L, auth(7L, "MEDICO")).getStatusCode().value());
        verifyNoInteractions(pagos);
    }

    @Test
    void pacienteNoLeePagosDeCitaAjena() {
        when(citas.obtenerPorId(1L)).thenReturn(citaDeOtroMedico());
        assertEquals(403, pagoController.obtenerPagosPorCita(1L, auth(7L, "PACIENTE")).getStatusCode().value());
        verifyNoInteractions(pagos);
    }

    @Test
    void medicoPropioYPacientePropioPuedenLeerPagos() {
        when(citas.obtenerPorId(1L)).thenReturn(citaDeOtroMedico());
        assertEquals(200, pagoController.obtenerPagosPorCita(1L, auth(99L, "MEDICO")).getStatusCode().value());
        assertEquals(200, pagoController.obtenerPagosPorCita(1L, auth(2L, "PACIENTE")).getStatusCode().value());
        verify(pagos, times(2)).obtenerPagosPorCita(1L);
    }

    @Test
    void medicoNoPuedeSuplantarPacienteAlPagar() {
        PagoTarjetaRequest request = new PagoTarjetaRequest();
        request.setUsuarioId(2L);
        pagoController.pagarTarjeta(request, auth(7L, "MEDICO"));
        assertEquals(7L, request.getUsuarioId());
        verify(pagos).pagarTarjeta(request);
    }

    @Test
    void adminYRecepcionConservanAccesoGlobal() {
        for (String rol : List.of("ADMIN", "RECEPCION")) {
            Authentication auth = auth(7L, rol);
            assertEquals(200, citaController.listarCitasPorUsuario(2L, auth).getStatusCode().value());
            assertEquals(200, citaController.listarCitasPorMedico(9L, auth).getStatusCode().value());
            assertEquals(200, citaController.confirmarReprogramar(1L, auth).getStatusCode().value());
            assertEquals(200, citaController.rechazarReprogramar(1L, auth).getStatusCode().value());
            assertEquals(200, pagoController.obtenerPagosPorCita(1L, auth).getStatusCode().value());
            assertEquals(200, pagoController.obtenerPagosPorMedico(9L, auth).getStatusCode().value());
        }
        verify(citas, never()).esMedicoPropio(anyLong(), anyLong());
        verify(citas, never()).obtenerPorId(anyLong());
    }

    @Test
    void solicitudImpagaRechazadaDevuelve400NoErrorInterno() {
        when(citas.obtenerPorId(1L)).thenReturn(citaDeOtroMedico());
        doThrow(new com.clinica.real.madrid.backend_citas.exception.BadRequestException("Reserva pendiente de pago"))
                .when(citas).solicitarCancelacion(1L, "motivo");
        assertEquals(400, citaController.solicitarCancelar(1L, java.util.Map.of("motivo", "motivo"),
                auth(2L, "PACIENTE")).getStatusCode().value());
    }
}
