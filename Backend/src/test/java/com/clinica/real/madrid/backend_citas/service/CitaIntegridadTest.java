package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.model.*;
import com.clinica.real.madrid.backend_citas.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitaIntegridadTest {
    @Mock CitaRepository citaRepository;
    @Mock DisponibilidadRepository disponibilidadRepository;
    @Mock PagoRepository pagoRepository;
    @Mock PagoService pagoService;
    @Mock EntityManager entityManager;
    @Mock PlatformTransactionManager transactionManager;
    @InjectMocks CitaService service;

    @AfterEach
    void limpiarSincronizacion() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void aplazarNotificaciones() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
    }

    private Cita cita(EstadoCita estado) {
        Cita cita = new Cita();
        cita.setId(1L);
        cita.setEstado(estado);
        cita.setFechaCreacion(LocalDateTime.now().minusHours(1));
        when(citaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cita));
        return cita;
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCita.class, names = {"PENDIENTE", "CANCELADA"})
    void reservasImpagasOCanceladasNoSolicitanCambios(EstadoCita estado) {
        cita(estado);
        assertThrows(BadRequestException.class, () -> service.solicitarCancelacion(1L, "motivo"));
        assertThrows(BadRequestException.class, () -> service.solicitarReprogramacion(1L, LocalDateTime.now().plusDays(1), "motivo"));
        verify(citaRepository, never()).save(any());
    }

    @Test
    void canceladaNoSeReactivaPorConfirmacionRechazoOReprogramacion() {
        Cita cita = cita(EstadoCita.CANCELADA);
        assertThrows(BadRequestException.class, () -> service.confirmarCita(1L));
        assertThrows(BadRequestException.class, () -> service.confirmarReprogramacion(1L));
        assertThrows(BadRequestException.class, () -> service.rechazarReprogramacion(1L));
        assertThrows(BadRequestException.class, () -> service.rechazarCancelacion(1L));
        assertThrows(BadRequestException.class, () -> service.reprogramarCita(1L, LocalDateTime.now().plusDays(1)));
        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
        verify(citaRepository, never()).save(any());
    }

    @Test
    void crearIgnoraFechaCreacionYEstadoDelCliente() {
        Cita cita = new Cita();
        Medico medico = new Medico();
        medico.setId(9L);
        cita.setMedico(medico);
        cita.setId(100L);
        cita.setEstado(EstadoCita.CONFIRMADA);
        cita.setFecha(LocalDateTime.now().plusDays(1));
        cita.setFechaCreacion(LocalDateTime.now().plusYears(10));
        cita.setFechaPropuesta(LocalDateTime.now().plusYears(1));
        when(disponibilidadRepository.findSlotForUpdate(eq(9L), any(), any(), any()))
                .thenReturn(Optional.of(new Disponibilidad()));
        when(citaRepository.save(cita)).thenReturn(cita);
        LocalDateTime inicio = LocalDateTime.now();
        service.crearCita(cita);
        assertNull(cita.getId());
        assertNull(cita.getFechaPropuesta());
        assertEquals(EstadoCita.PENDIENTE, cita.getEstado());
        assertFalse(cita.getFechaCreacion().isBefore(inicio));
        assertFalse(cita.getFechaCreacion().isAfter(LocalDateTime.now()));
    }

    @Test
    void cancelacionNoLiberaHorarioSiFallaReembolso() {
        Cita cita = cita(EstadoCita.SOLICITUD_CANCELACION);
        doThrow(new BadRequestException("Stripe offline")).when(pagoService).reembolsarPago(1L);
        assertThrows(BadRequestException.class, () -> service.confirmarCancelacion(1L));
        assertEquals(EstadoCita.SOLICITUD_CANCELACION, cita.getEstado());
        verifyNoInteractions(disponibilidadRepository);
        verify(citaRepository, never()).save(any());
    }

    @Test
    void schedulerRevalidaBajoBloqueoYRespetaPagoConcurrente() {
        Cita cita = cita(EstadoCita.CONFIRMADA);
        when(citaRepository.findIdsAbandonadas(eq(EstadoCita.PENDIENTE), any())).thenReturn(List.of(1L));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service.liberarCitasPendientesAbandonadas();
        verify(citaRepository).findByIdForUpdate(1L);
        verify(entityManager).refresh(cita, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        verify(transactionManager).commit(any());
        verifyNoInteractions(pagoService, disponibilidadRepository);
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
    }

    @Test
    void schedulerReviertePagoAntesDeCancelarYLiberaReservaExpirada() {
        Cita cita = cita(EstadoCita.PENDIENTE);
        Medico medico = new Medico();
        medico.setId(9L);
        cita.setMedico(medico);
        cita.setFecha(LocalDateTime.now().plusDays(1));
        when(citaRepository.findIdsAbandonadas(eq(EstadoCita.PENDIENTE), any())).thenReturn(List.of(1L));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Disponibilidad disponibilidad = new Disponibilidad();
        when(disponibilidadRepository.findFirstByMedicoIdAndFechaAndHoraInicio(eq(9L), any(), any()))
                .thenReturn(Optional.of(disponibilidad));
        doAnswer(invocation -> {
            assertEquals(EstadoCita.PENDIENTE, cita.getEstado());
            return null;
        }).when(pagoService).reembolsarPago(1L);
        service.liberarCitasPendientesAbandonadas();
        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
        assertEquals(Disponibilidad.EstadoDisponibilidad.DISPONIBLE, disponibilidad.getEstado());
        var orden = inOrder(pagoService, disponibilidadRepository);
        orden.verify(pagoService).reembolsarPago(1L);
        orden.verify(disponibilidadRepository).findFirstByMedicoIdAndFechaAndHoraInicio(eq(9L), any(), any());
        orden.verify(disponibilidadRepository).save(disponibilidad);
        verify(transactionManager).commit(any());
    }

    @Test
    void staffConfirmaReservaSinPagoYReintentaSinReactivarCanceladas() {
        Cita cita = cita(EstadoCita.PENDIENTE);
        aplazarNotificaciones();
        service.confirmarCita(1L);
        service.confirmarCita(1L);
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
        verify(citaRepository).save(cita);
        verifyNoInteractions(pagoRepository);
        assertEquals(1, org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().size());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCita.class, names = {"PENDIENTE", "CONFIRMADA", "SOLICITUD_REPROGRAMACION"})
    void staffPuedeCancelarDirectamenteSinSolicitudPrevia(EstadoCita estado) {
        Cita cita = cita(estado);
        Medico medico = new Medico();
        medico.setId(9L);
        cita.setMedico(medico);
        cita.setFecha(LocalDateTime.now().plusDays(1));
        aplazarNotificaciones();
        service.confirmarCancelacion(1L);
        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
        verify(pagoService).reembolsarPago(1L);
        verify(citaRepository).save(cita);
        assertEquals(1, org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().size());
    }

    @Test
    void staffReprogramaReservaImpagaSinSacarlaDelCleanup() {
        Cita cita = cita(EstadoCita.PENDIENTE);
        LocalDateTime creada = cita.getFechaCreacion();
        Medico medico = new Medico();
        medico.setId(9L);
        cita.setMedico(medico);
        cita.setFecha(LocalDateTime.now().plusDays(1));
        when(disponibilidadRepository.findSlotForUpdate(eq(9L), any(), any(), any()))
                .thenReturn(Optional.of(new Disponibilidad()));
        aplazarNotificaciones();
        LocalDateTime nuevaFecha = LocalDateTime.now().plusDays(2);
        service.reprogramarCita(1L, nuevaFecha);
        assertEquals(nuevaFecha, cita.getFecha());
        assertEquals(EstadoCita.PENDIENTE, cita.getEstado());
        assertEquals(creada, cita.getFechaCreacion());
    }

    @Test
    void schedulerFallaUnaReservaHaceRollbackYContinuaEnTransaccionNueva() {
        Cita primera = cita(EstadoCita.PENDIENTE);
        Cita segunda = new Cita();
        segunda.setId(2L);
        segunda.setEstado(EstadoCita.CONFIRMADA);
        when(citaRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(segunda));
        when(citaRepository.findIdsAbandonadas(eq(EstadoCita.PENDIENTE), any())).thenReturn(List.of(1L, 2L));
        var fallida = new SimpleTransactionStatus();
        var exitosa = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(fallida, exitosa);
        doThrow(new BadRequestException("Stripe offline")).when(pagoService).reembolsarPago(1L);
        service.liberarCitasPendientesAbandonadas();
        verify(transactionManager).rollback(fallida);
        verify(transactionManager).commit(exitosa);
        var definiciones = org.mockito.ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(transactionManager, times(2)).getTransaction(definiciones.capture());
        assertTrue(definiciones.getAllValues().stream().allMatch(d ->
                d.getPropagationBehavior() == org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        assertEquals(EstadoCita.PENDIENTE, primera.getEstado());
        verifyNoInteractions(disponibilidadRepository);
    }

    @Test
    void schedulerRefreshDescartaEstadoObsoletoTrasEsperarBloqueo() {
        Cita cita = cita(EstadoCita.PENDIENTE);
        when(citaRepository.findIdsAbandonadas(eq(EstadoCita.PENDIENTE), any())).thenReturn(List.of(1L));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doAnswer(invocation -> {
            cita.setEstado(EstadoCita.CONFIRMADA);
            return null;
        }).when(entityManager).refresh(cita, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        service.liberarCitasPendientesAbandonadas();
        verifyNoInteractions(pagoService, disponibilidadRepository);
        verify(citaRepository, never()).save(any());
    }

    @Test
    void notificacionesNoSeEnvianAntesDeCommitNiPorRollback() {
        cita(EstadoCita.PENDIENTE);
        aplazarNotificaciones();
        // No patient/doctor is initialized: eager notification construction would fail here.
        service.confirmarCita(1L);
        var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, callbacks.size());
        callbacks.forEach(c -> c.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(pagoService, disponibilidadRepository);
    }
}
