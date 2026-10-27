package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.dto.*;
import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.model.*;
import com.clinica.real.madrid.backend_citas.repository.*;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoIntegridadTest {
    @Mock PagoRepository pagoRepository;
    @Mock CitaRepository citaRepository;
    @Mock EntityManager entityManager;
    @Mock org.springframework.transaction.PlatformTransactionManager transactionManager;
    @InjectMocks PagoService service;
    Cita cita;
    Pago pago;

    @BeforeEach
    void preparar() {
        Usuario paciente = new Usuario();
        paciente.setId(2L);
        Especialidad especialidad = new Especialidad();
        especialidad.setPrecioBase(100.0);
        Medico medico = new Medico();
        medico.setEspecialidad(especialidad);
        cita = new Cita();
        cita.setId(1L);
        cita.setPaciente(paciente);
        cita.setMedico(medico);
        cita.setEstado(EstadoCita.CONFIRMADA);
        pago = new Pago();
        pago.setId(3L);
        pago.setCita(cita);
        pago.setUsuario(paciente);
        pago.setMetodo(MetodoPago.EFECTIVO);
        pago.setMonto(new BigDecimal("100.00"));
        pago.setEstadoPago(EstadoPago.PENDIENTE);
        when(citaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cita));
    }

    private void pagoPorId() {
        when(pagoRepository.findCitaIdByPagoId(3L)).thenReturn(Optional.of(1L));
        when(pagoRepository.findById(3L)).thenReturn(Optional.of(pago));
    }

    private PagoEfectivoRequest efectivo() {
        PagoEfectivoRequest request = new PagoEfectivoRequest();
        request.setCitaId(1L);
        request.setUsuarioId(2L);
        return request;
    }

    private PagoTarjetaRequest tarjeta() {
        pago.setMetodo(MetodoPago.TARJETA);
        pago.setTransaccionId("pi_test");
        PagoTarjetaRequest request = new PagoTarjetaRequest();
        request.setCitaId(1L);
        request.setUsuarioId(2L);
        request.setReferencia("pi_test");
        request.setExito(true);
        return request;
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPago.class, names = {"COMPLETADO", "ANULADO", "REEMBOLSADO"})
    void completarRechazaEstadosNoPendientes(EstadoPago estado) {
        pagoPorId();
        pago.setEstadoPago(estado);
        assertThrows(BadRequestException.class, () -> service.completarPago(3L));
        verify(pagoRepository, never()).save(any());
        assertEquals(estado, pago.getEstadoPago());
    }

    @Test
    void completarNoApruebaTarjetaPendiente() {
        pagoPorId();
        pago.setMetodo(MetodoPago.TARJETA);
        assertThrows(BadRequestException.class, () -> service.completarPago(3L));
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void completarRefrescaCitaYNoReactivaCancelada() {
        pagoPorId();
        doAnswer(invocation -> { cita.setEstado(EstadoCita.CANCELADA); return null; })
                .when(entityManager).refresh(cita, LockModeType.PESSIMISTIC_WRITE);
        assertThrows(BadRequestException.class, () -> service.completarPago(3L));
        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void completarEfectivoValidoBloqueaAntesDeLeerPago() {
        pagoPorId();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        when(pagoRepository.save(pago)).thenReturn(pago);
        assertEquals("COMPLETADO", service.completarPago(3L).getEstadoPago());
        assertNotNull(pago.getComprobante());
        var orden = inOrder(citaRepository, entityManager, pagoRepository);
        orden.verify(pagoRepository).findCitaIdByPagoId(3L);
        orden.verify(citaRepository).findByIdForUpdate(1L);
        orden.verify(entityManager).refresh(cita, LockModeType.PESSIMISTIC_WRITE);
        orden.verify(pagoRepository).findById(3L);
    }

    @Test
    void promesaReintentadaDevuelveLaMismaSinDuplicar() {
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        assertEquals(3L, service.prometerPagoEfectivo(efectivo()).getId());
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void efectivoCompletaPromesaExistenteSinCrearOtra() {
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        when(pagoRepository.save(pago)).thenReturn(pago);
        assertEquals(3L, service.pagarEfectivo(efectivo()).getId());
        verify(pagoRepository).save(pago);
        assertEquals(EstadoPago.COMPLETADO, pago.getEstadoPago());
    }

    @Test
    void efectivoNoCompiteConIntentoTarjeta() {
        pago.setMetodo(MetodoPago.TARJETA);
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        assertThrows(BadRequestException.class, () -> service.pagarEfectivo(efectivo()));
        assertThrows(BadRequestException.class, () -> service.prometerPagoEfectivo(efectivo()));
    }

    @Test
    void efectivoNoPermiteOtroPaciente() {
        PagoEfectivoRequest request = efectivo();
        request.setUsuarioId(99L);
        assertThrows(BadRequestException.class, () -> service.pagarEfectivo(request));
        verify(pagoRepository, never()).findByCitaIdForUpdate(anyLong());
    }

    @Test
    void pagosNoCambianCitasCanceladas() {
        cita.setEstado(EstadoCita.CANCELADA);
        PaymentIntentRequest intentRequest = new PaymentIntentRequest();
        intentRequest.setCitaId(1L);
        intentRequest.setUsuarioId(2L);
        assertThrows(BadRequestException.class, () -> service.crearPaymentIntent(intentRequest));
        assertThrows(BadRequestException.class, () -> service.pagarEfectivo(efectivo()));
        assertThrows(BadRequestException.class, () -> service.prometerPagoEfectivo(efectivo()));
        assertThrows(BadRequestException.class, () -> service.pagarTarjeta(tarjeta()));
        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
        verify(pagoRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCita.class, names = {"PENDIENTE", "CANCELADA"}, mode = EnumSource.Mode.EXCLUDE)
    void cobroEfectivoRespetaEstadosActivosYSolicitudes(EstadoCita estado) {
        cita.setEstado(estado);
        pagoPorId();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        when(pagoRepository.save(pago)).thenReturn(pago);
        service.completarPago(3L);
        assertEquals(EstadoPago.COMPLETADO, pago.getEstadoPago());
        assertEquals(estado, cita.getEstado());
        verify(citaRepository, never()).save(any());
    }

    @Test
    void trasTarjetaRechazadaPuedeElegirEfectivoSinDuplicarCobro() throws Exception {
        tarjeta();
        cita.setEstado(EstadoCita.PENDIENTE);
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("requires_payment_method");
        when(intent.cancel()).thenReturn(intent);
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            PagoResponse response = service.prometerPagoEfectivo(efectivo());
            assertEquals("EFECTIVO", response.getMetodo());
            assertEquals("PENDIENTE", response.getEstadoPago());
            assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
            assertEquals(EstadoPago.ANULADO, pago.getEstadoPago());
            var orden = inOrder(intent, pagoRepository);
            orden.verify(intent).cancel();
            orden.verify(pagoRepository).save(pago);
            orden.verify(pagoRepository).save(argThat(p -> p != pago && p.getMetodo() == MetodoPago.EFECTIVO));
        }
    }

    @Test
    void cobroDirectoEfectivoTambienPermiteCambiarIntentoNoCobrado() throws Exception {
        tarjeta();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("canceled");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            assertEquals("COMPLETADO", service.pagarEfectivo(efectivo()).getEstadoPago());
            verify(intent, never()).cancel();
            assertEquals(EstadoPago.ANULADO, pago.getEstadoPago());
        }
    }

    @Test
    void cambioAEfectivoNoReembolsaTarjetaYaCobrada() throws Exception {
        tarjeta();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("succeeded");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class);
             MockedStatic<Refund> refunds = mockStatic(Refund.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            assertThrows(BadRequestException.class, () -> service.prometerPagoEfectivo(efectivo()));
            assertThrows(BadRequestException.class, () -> service.pagarEfectivo(efectivo()));
            verify(intent, never()).cancel();
            refunds.verifyNoInteractions();
            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    void falloAlCancelarIntentoNoCreaPromesaNiConfirmaCita() throws Exception {
        tarjeta();
        cita.setEstado(EstadoCita.PENDIENTE);
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("requires_payment_method");
        when(intent.cancel()).thenThrow(new ApiConnectionException("offline"));
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            assertThrows(BadRequestException.class, () -> service.prometerPagoEfectivo(efectivo()));
            assertEquals(EstadoPago.PENDIENTE, pago.getEstadoPago());
            assertEquals(EstadoCita.PENDIENTE, cita.getEstado());
            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    void intentNoCompiteConPromesaEfectivo() {
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntentRequest request = new PaymentIntentRequest();
        request.setCitaId(1L);
        request.setUsuarioId(2L);
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            assertThrows(BadRequestException.class, () -> service.crearPaymentIntent(request));
            stripe.verifyNoInteractions();
        }
    }

    @Test
    void intentRetryReutilizaStripeExistente() throws Exception {
        tarjeta();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntentRequest request = new PaymentIntentRequest();
        request.setCitaId(1L);
        request.setUsuarioId(2L);
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_test");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            service.crearPaymentIntent(request);
            stripe.verify(() -> PaymentIntent.retrieve("pi_test"));
            stripe.verifyNoMoreInteractions();
            verify(pagoRepository, never()).saveAndFlush(any());
        }
    }

    @Test
    void intentCanceladoTrasRollbackSeReemplazaParaPermitirReintento() throws Exception {
        tarjeta();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentIntentRequest request = new PaymentIntentRequest();
        request.setCitaId(1L);
        request.setUsuarioId(2L);
        PaymentIntent cancelado = new PaymentIntent();
        cancelado.setStatus("canceled");
        PaymentIntent nuevo = new PaymentIntent();
        nuevo.setId("pi_nuevo");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(cancelado);
            stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenAnswer(invocation -> {
                        RequestOptions options = invocation.getArgument(1);
                        assertEquals("cita-intent-1-1", options.getIdempotencyKey());
                        return nuevo;
                    });
            assertEquals("pi_nuevo", service.crearPaymentIntent(request).getId());
            assertEquals(EstadoPago.ANULADO, pago.getEstadoPago());
            verify(pagoRepository).save(pago);
        }
    }

    @Test
    void intentUsaPrecioServidorYClaveEstableAunqueRollbackCambieId() throws Exception {
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of());
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenAnswer(invocation -> {
            Pago nuevo = invocation.getArgument(0);
            nuevo.setId(55L);
            return nuevo;
        });
        PaymentIntentRequest request = new PaymentIntentRequest();
        request.setCitaId(1L);
        request.setUsuarioId(2L);
        request.setMonto(0.01);
        request.setMoneda("usd");
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_test");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenAnswer(invocation -> {
                        PaymentIntentCreateParams params = invocation.getArgument(0);
                        RequestOptions options = invocation.getArgument(1);
                        assertEquals(10000L, params.getAmount());
                        assertEquals("pen", params.getCurrency());
                        assertEquals("cita-intent-1-0", options.getIdempotencyKey());
                        return intent;
                    });
            service.crearPaymentIntent(request);
            service.crearPaymentIntent(request);
            stripe.verify(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)), times(2));
        }
    }

    @Test
    void tarjetaRetryCompletadaNoDuplicaComprobanteNiConsultaStripe() {
        PagoTarjetaRequest request = tarjeta();
        pago.setEstadoPago(EstadoPago.COMPLETADO);
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            assertEquals(3L, service.pagarTarjeta(request).getId());
            stripe.verifyNoInteractions();
            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    void tarjetaValidaMetadataYMontoDelServidor() {
        PagoTarjetaRequest request = tarjeta();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntent intent = new PaymentIntent();
        intent.setStatus("succeeded");
        intent.setCurrency("pen");
        intent.setAmount(10000L);
        intent.setMetadata(Map.of("citaId", "999", "usuarioId", "2"));
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            assertThrows(BadRequestException.class, () -> service.pagarTarjeta(request));
            assertEquals(EstadoPago.PENDIENTE, pago.getEstadoPago());
            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    void tarjetaConfirmadaPorStripeCompletaElMismoPago() {
        PagoTarjetaRequest request = tarjeta();
        cita.setEstado(EstadoCita.PENDIENTE);
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        when(pagoRepository.save(pago)).thenReturn(pago);
        PaymentIntent intent = new PaymentIntent();
        intent.setStatus("succeeded");
        intent.setCurrency("pen");
        intent.setAmount(10000L);
        intent.setMetadata(Map.of("citaId", "1", "usuarioId", "2"));
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            assertEquals(3L, service.pagarTarjeta(request).getId());
            assertEquals(EstadoPago.COMPLETADO, pago.getEstadoPago());
            assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
            verify(pagoRepository).save(pago);
        }
    }

    @Test
    void confirmaIntentoEmitidoAntesDelDespliegueSinPagoLocal() {
        PagoTarjetaRequest request = tarjeta();
        cita.setEstado(EstadoCita.PENDIENTE);
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of());
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentIntent intent = new PaymentIntent();
        intent.setStatus("succeeded");
        intent.setCurrency("pen");
        intent.setAmount(10000L);
        intent.setMetadata(Map.of("citaId", "1", "usuarioId", "2"));
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            PagoResponse response = service.pagarTarjeta(request);
            assertEquals("COMPLETADO", response.getEstadoPago());
            assertEquals("pi_test", response.getTransaccionId());
            assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
            verify(pagoRepository).existsByTransaccionId("pi_test");
            verify(pagoRepository).save(argThat(p -> p.getMetodo() == MetodoPago.TARJETA
                    && p.getMonto().compareTo(new BigDecimal("100.00")) == 0));
        }
    }

    @Test
    void noAdoptaReferenciaYaUsadaPorPagoTerminal() {
        PagoTarjetaRequest request = tarjeta();
        pago.setEstadoPago(EstadoPago.REEMBOLSADO);
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        when(pagoRepository.existsByTransaccionId("pi_test")).thenReturn(true);
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            assertThrows(BadRequestException.class, () -> service.pagarTarjeta(request));
            stripe.verifyNoInteractions();
            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    void stripeExceptionCheckedProvocaRollbackDelRequestConProxySpring() throws Exception {
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of());
        when(pagoRepository.saveAndFlush(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var estado = new org.springframework.transaction.support.SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(estado);
        var interceptor = new org.springframework.transaction.interceptor.TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(interceptor);
        PagoService proxy = (PagoService) factory.getProxy();
        PaymentIntentRequest request = new PaymentIntentRequest();
        request.setCitaId(1L);
        request.setUsuarioId(2L);
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new ApiConnectionException("timeout"));
            assertThrows(ApiConnectionException.class, () -> proxy.crearPaymentIntent(request));
            verify(transactionManager).rollback(estado);
            verify(transactionManager, never()).commit(any());
            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    void reembolsoFallidoNoAnulaNiGuarda() throws Exception {
        pagoPorId();
        tarjeta();
        pago.setEstadoPago(EstadoPago.COMPLETADO);
        PaymentIntent intent = new PaymentIntent();
        intent.setStatus("succeeded");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class);
             MockedStatic<Refund> refunds = mockStatic(Refund.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            refunds.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new ApiConnectionException("offline"));
            assertThrows(BadRequestException.class, () -> service.anularPago(3L));
            assertEquals(EstadoPago.COMPLETADO, pago.getEstadoPago());
            verify(pagoRepository, never()).save(any());
        }
    }

    @Test
    void reembolsoPendienteNoSeDeclaraExitoso() {
        tarjeta();
        pago.setEstadoPago(EstadoPago.COMPLETADO);
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntent intent = new PaymentIntent();
        intent.setStatus("succeeded");
        Refund refund = new Refund();
        refund.setStatus("pending");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class);
             MockedStatic<Refund> refunds = mockStatic(Refund.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            refunds.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class))).thenReturn(refund);
            assertThrows(BadRequestException.class, () -> service.reembolsarPago(1L));
            assertEquals(EstadoPago.COMPLETADO, pago.getEstadoPago());
        }
    }

    @Test
    void cancelarIntentoPendienteInvalidaStripeAntesDeAnular() throws Exception {
        tarjeta();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getStatus()).thenReturn("requires_payment_method");
        when(intent.cancel()).thenAnswer(invocation -> {
            assertEquals(EstadoPago.PENDIENTE, pago.getEstadoPago());
            return intent;
        });
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            service.reembolsarPago(1L);
            verify(intent).cancel();
            assertEquals(EstadoPago.ANULADO, pago.getEstadoPago());
            verify(pagoRepository).save(pago);
        }
    }

    @Test
    void intentoCobradoSinCallbackSeReembolsaEnLugarDeSoloAnular() {
        tarjeta();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntent intent = new PaymentIntent();
        intent.setStatus("succeeded");
        Refund refund = new Refund();
        refund.setStatus("succeeded");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class);
             MockedStatic<Refund> refunds = mockStatic(Refund.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            refunds.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class))).thenReturn(refund);
            service.reembolsarPago(1L);
            assertEquals(EstadoPago.REEMBOLSADO, pago.getEstadoPago());
            verify(pagoRepository).save(pago);
        }
    }

    @Test
    void retryReembolsoConsultaEstadoActualNoRespuestaPendingCacheada() {
        tarjeta();
        when(pagoRepository.findByCitaIdForUpdate(1L)).thenReturn(List.of(pago));
        PaymentIntent intent = new PaymentIntent();
        intent.setStatus("succeeded");
        Refund cacheado = new Refund();
        cacheado.setId("re_test");
        cacheado.setStatus("pending");
        Refund actual = new Refund();
        actual.setId("re_test");
        actual.setStatus("succeeded");
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class);
             MockedStatic<Refund> refunds = mockStatic(Refund.class)) {
            stripe.when(() -> PaymentIntent.retrieve("pi_test")).thenReturn(intent);
            refunds.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class))).thenReturn(cacheado);
            refunds.when(() -> Refund.retrieve("re_test")).thenReturn(actual);
            service.reembolsarPago(1L);
            assertEquals(EstadoPago.REEMBOLSADO, pago.getEstadoPago());
            refunds.verify(() -> Refund.retrieve("re_test"));
        }
    }
}
