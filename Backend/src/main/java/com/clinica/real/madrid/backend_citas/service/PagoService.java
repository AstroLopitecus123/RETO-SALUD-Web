package com.clinica.real.madrid.backend_citas.service;

import com.clinica.real.madrid.backend_citas.dto.*;
import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.model.*;
import com.clinica.real.madrid.backend_citas.repository.CitaRepository;
import com.clinica.real.madrid.backend_citas.repository.PagoRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PagoService {
    @Autowired
    private PagoRepository pagoRepository;
    @Autowired
    private CitaRepository citaRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    // Every payment mutation takes the appointment lock before reading its payments.
    private Cita bloquearCita(Long id) {
        Cita cita = citaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BadRequestException("Cita no encontrada"));
        entityManager.refresh(cita, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return cita;
    }

    private void validarCita(Cita cita, Long usuarioId) {
        if (cita.getPaciente() == null || usuarioId == null || !usuarioId.equals(cita.getPaciente().getId())) {
            throw new BadRequestException("El pago no corresponde al paciente de la cita");
        }
        if (cita.getEstado() == null || cita.getEstado() == EstadoCita.CANCELADA) {
            throw new BadRequestException("El estado de la cita no permite registrar pagos");
        }
    }

    private BigDecimal precio(Cita cita) {
        Double valor = cita.getMedico() != null && cita.getMedico().getEspecialidad() != null
                ? cita.getMedico().getEspecialidad().getPrecioBase() : null;
        if (valor == null || !Double.isFinite(valor) || valor <= 0) {
            throw new BadRequestException("La especialidad medica no tiene un precio asignado valido");
        }
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private Pago nuevoPago(Cita cita, MetodoPago metodo) {
        Pago pago = new Pago();
        pago.setCita(cita);
        pago.setUsuario(cita.getPaciente());
        pago.setMonto(precio(cita));
        pago.setMetodo(metodo);
        pago.setEstadoPago(EstadoPago.PENDIENTE);
        return pago;
    }

    private Pago pagoActivo(List<Pago> pagos) {
        List<Pago> activos = pagos.stream().filter(p -> p.getEstadoPago() == EstadoPago.PENDIENTE
                || p.getEstadoPago() == EstadoPago.COMPLETADO).collect(Collectors.toList());
        if (activos.size() > 1) throw new BadRequestException("La cita tiene pagos duplicados; requiere revision");
        return activos.isEmpty() ? null : activos.get(0);
    }

    private Pago pagoParaEfectivo(Cita cita) {
        Pago pago = pagoActivo(pagoRepository.findByCitaIdForUpdate(cita.getId()));
        if (pago == null || pago.getMetodo() == MetodoPago.EFECTIVO) return pago;
        if (pago.getEstadoPago() != EstadoPago.PENDIENTE || pago.getTransaccionId() == null) {
            throw new BadRequestException("La cita ya tiene un pago con otro metodo");
        }
        try {
            PaymentIntent intent = PaymentIntent.retrieve(pago.getTransaccionId());
            if ("succeeded".equals(intent.getStatus()) || "processing".equals(intent.getStatus())
                    || "requires_capture".equals(intent.getStatus())) {
                throw new BadRequestException("El pago tarjeta ya fue cobrado o esta en proceso; no se puede cambiar a efectivo");
            }
            if (!"canceled".equals(intent.getStatus())) intent.cancel();
        } catch (StripeException e) {
            throw new BadRequestException("No se pudo cancelar el intento tarjeta; reintenta antes de cambiar a efectivo");
        }
        pago.setEstadoPago(EstadoPago.ANULADO);
        pagoRepository.save(pago);
        return null;
    }

    @Transactional(rollbackFor = StripeException.class)
    public PaymentIntentResponse crearPaymentIntent(PaymentIntentRequest request) throws StripeException {
        Cita cita = bloquearCita(request.getCitaId());
        // Staff may omit the patient ID, but may not substitute a different patient.
        Long usuarioId = request.getUsuarioId() != null ? request.getUsuarioId()
                : cita.getPaciente() != null ? cita.getPaciente().getId() : null;
        validarCita(cita, usuarioId);
        List<Pago> pagos = pagoRepository.findByCitaIdForUpdate(cita.getId());
        Pago pago = pagoActivo(pagos);
        PaymentIntent intent;
        if (pago != null) {
            if (pago.getMetodo() != MetodoPago.TARJETA || pago.getEstadoPago() != EstadoPago.PENDIENTE
                    || pago.getTransaccionId() == null) {
                throw new BadRequestException("La cita ya tiene un pago registrado");
            }
            intent = PaymentIntent.retrieve(pago.getTransaccionId());
            if (!"canceled".equals(intent.getStatus())) {
                return new PaymentIntentResponse(intent.getClientSecret(), intent.getId(), intent.getStatus());
            }
            // A previous request can cancel Stripe and then roll back the local state.
            pago.setEstadoPago(EstadoPago.ANULADO);
            pagoRepository.save(pago);
        }
        pago = pagoRepository.saveAndFlush(nuevoPago(cita, MetodoPago.TARJETA));
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(pago.getMonto().movePointRight(2).longValueExact()).setCurrency("pen")
                .addPaymentMethodType("card").putMetadata("citaId", cita.getId().toString())
                .putMetadata("usuarioId", usuarioId.toString()).build();
        // Use committed history, not the generated ID: a Stripe timeout can roll back that ID.
        intent = PaymentIntent.create(params, RequestOptions.builder()
                .setIdempotencyKey("cita-intent-" + cita.getId() + "-" + pagos.size()).build());
        pago.setTransaccionId(intent.getId());
        pagoRepository.save(pago);
        return new PaymentIntentResponse(intent.getClientSecret(), intent.getId(), intent.getStatus());
    }

    @Transactional
    public PagoResponse prometerPagoEfectivo(PagoEfectivoRequest request) {
        Cita cita = bloquearCita(request.getCitaId());
        validarCita(cita, request.getUsuarioId());
        Pago pago = pagoParaEfectivo(cita);
        if (pago != null) {
            if (pago.getMetodo() == MetodoPago.EFECTIVO && pago.getEstadoPago() == EstadoPago.PENDIENTE) {
                return mapToResponse(pago);
            }
            throw new BadRequestException("La cita ya tiene un pago registrado");
        }
        pago = pagoRepository.save(nuevoPago(cita, MetodoPago.EFECTIVO));
        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);
        return mapToResponse(pago);
    }

    @Transactional
    public PagoResponse pagarEfectivo(PagoEfectivoRequest request) {
        Cita cita = bloquearCita(request.getCitaId());
        validarCita(cita, request.getUsuarioId());
        Pago pago = pagoParaEfectivo(cita);
        if (pago != null && pago.getEstadoPago() == EstadoPago.COMPLETADO) return mapToResponse(pago);
        if (pago == null) pago = nuevoPago(cita, MetodoPago.EFECTIVO);
        completar(pago, cita);
        return mapToResponse(pagoRepository.save(pago));
    }

    @Transactional
    public PagoResponse completarPago(Long pagoId) {
        Long citaId = pagoRepository.findCitaIdByPagoId(pagoId)
                .orElseThrow(() -> new BadRequestException("Pago no encontrado"));
        Cita cita = bloquearCita(citaId);
        Pago pago = pagoRepository.findById(pagoId).orElseThrow(() -> new BadRequestException("Pago no encontrado"));
        entityManager.refresh(pago, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        validarCita(cita, pago.getUsuario() != null ? pago.getUsuario().getId() : null);
        if (pago.getMetodo() != MetodoPago.EFECTIVO || pago.getEstadoPago() != EstadoPago.PENDIENTE) {
            throw new BadRequestException("Solo se puede completar un pago efectivo pendiente");
        }
        Pago activo = pagoActivo(pagoRepository.findByCitaIdForUpdate(citaId));
        if (activo == null || !pagoId.equals(activo.getId())) {
            throw new BadRequestException("El pago no es el pago activo de la cita");
        }
        completar(pago, cita);
        return mapToResponse(pagoRepository.save(pago));
    }

    private void completar(Pago pago, Cita cita) {
        pago.setEstadoPago(EstadoPago.COMPLETADO);
        pago.setFechaPago(LocalDateTime.now());
        if (pago.getComprobante() == null) {
            Comprobante comprobante = new Comprobante();
            comprobante.setNumero("FACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            comprobante.setFecha(LocalDateTime.now());
            comprobante.setPago(pago);
            pago.setComprobante(comprobante);
        }
        if (cita.getEstado() == EstadoCita.PENDIENTE) {
            cita.setEstado(EstadoCita.CONFIRMADA);
            citaRepository.save(cita);
        }
    }

    @Transactional
    public PagoResponse pagarTarjeta(PagoTarjetaRequest request) {
        Cita cita = bloquearCita(request.getCitaId());
        validarCita(cita, request.getUsuarioId());
        String referencia = request.getReferencia() == null ? "" : request.getReferencia().trim();
        List<Pago> pagos = pagoRepository.findByCitaIdForUpdate(cita.getId());
        Pago pago = pagoActivo(pagos);
        if (referencia.isEmpty() || pago != null && (pago.getMetodo() != MetodoPago.TARJETA
                || !referencia.equals(pago.getTransaccionId()))) {
            throw new BadRequestException("Debes utilizar el intento de pago registrado para esta cita");
        }
        // Intents issued before pending payments were persisted must still be confirmable.
        if (pago == null) {
            if (!Boolean.TRUE.equals(request.getExito()) || pagoRepository.existsByTransaccionId(referencia)) {
                throw new BadRequestException("No hay un intento tarjeta valido para confirmar");
            }
            pago = nuevoPago(cita, MetodoPago.TARJETA);
            pago.setTransaccionId(referencia);
        }
        if (pago.getEstadoPago() == EstadoPago.COMPLETADO) return mapToResponse(pago);
        if (!Boolean.TRUE.equals(request.getExito())) return mapToResponse(pago);
        try {
            PaymentIntent intent = PaymentIntent.retrieve(referencia);
            if (!"succeeded".equals(intent.getStatus()) || !"pen".equalsIgnoreCase(intent.getCurrency())
                    || intent.getAmount() == null || intent.getAmount() != pago.getMonto().movePointRight(2).longValueExact()
                    || intent.getMetadata() == null || !cita.getId().toString().equals(intent.getMetadata().get("citaId"))
                    || !request.getUsuarioId().toString().equals(intent.getMetadata().get("usuarioId"))) {
                throw new BadRequestException("Stripe no confirma un pago valido para esta cita y paciente");
            }
        } catch (StripeException e) {
            throw new BadRequestException("No se pudo validar el pago con Stripe");
        }
        completar(pago, cita);
        return mapToResponse(pagoRepository.save(pago));
    }

    public List<PagoResponse> obtenerPagosPorCita(Long citaId) {
        return pagoRepository.findByCitaId(citaId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<PagoResponse> obtenerTodosLosPagos() {
        return pagoRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<PagoResponse> obtenerPagosPorUsuario(Long usuarioId) {
        return pagoRepository.findByUsuarioId(usuarioId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<PagoResponse> obtenerPagosPorMedico(Long medicoId) {
        return pagoRepository.findByCita_Medico_Id(medicoId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public PagoResponse anularPago(Long pagoId) {
        Long citaId = pagoRepository.findCitaIdByPagoId(pagoId)
                .orElseThrow(() -> new BadRequestException("Pago no encontrado"));
        bloquearCita(citaId);
        Pago pago = pagoRepository.findById(pagoId).orElseThrow(() -> new BadRequestException("Pago no encontrado"));
        entityManager.refresh(pago, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        revertir(pago);
        return mapToResponse(pagoRepository.save(pago));
    }

    @Transactional
    public void reembolsarPago(Long citaId) {
        bloquearCita(citaId);
        for (Pago pago : pagoRepository.findByCitaIdForUpdate(citaId)) {
            revertir(pago);
            pagoRepository.save(pago);
        }
    }

    private void revertir(Pago pago) {
        if (pago.getEstadoPago() == EstadoPago.ANULADO || pago.getEstadoPago() == EstadoPago.REEMBOLSADO) return;
        if (pago.getMetodo() == MetodoPago.TARJETA) {
            if (pago.getTransaccionId() == null) throw new BadRequestException("Pago tarjeta sin referencia; requiere revision");
            try {
                PaymentIntent intent = PaymentIntent.retrieve(pago.getTransaccionId());
                if ("succeeded".equals(intent.getStatus())) {
                    Refund refund = Refund.create(RefundCreateParams.builder().setPaymentIntent(pago.getTransaccionId()).build(),
                            RequestOptions.builder().setIdempotencyKey("pago-refund-" + pago.getId()).build());
                    // Stripe replays the original idempotent response, even if its pending refund has since settled.
                    if (!"succeeded".equals(refund.getStatus()) && refund.getId() != null) {
                        refund = Refund.retrieve(refund.getId());
                    }
                    if (!"succeeded".equals(refund.getStatus())) {
                        throw new BadRequestException("El reembolso aun no esta confirmado; requiere revision");
                    }
                    pago.setEstadoPago(EstadoPago.REEMBOLSADO);
                    return;
                }
                if (!"canceled".equals(intent.getStatus())) {
                    intent.cancel();
                }
            } catch (StripeException e) {
                throw new BadRequestException("No se pudo revertir el pago con Stripe");
            }
        }
        pago.setEstadoPago(EstadoPago.ANULADO);
    }

    private PagoResponse mapToResponse(Pago pago) {
        PagoResponse response = new PagoResponse();
        response.setId(pago.getId());
        response.setCitaId(pago.getCita().getId());
        response.setUsuarioId(pago.getUsuario().getId());
        response.setMonto(pago.getMonto());
        response.setMetodo(pago.getMetodo().name());
        response.setEstadoPago(pago.getEstadoPago().name());
        response.setTransaccionId(pago.getTransaccionId());
        response.setPacienteNombre(pago.getUsuario().getNombre() + " " + pago.getUsuario().getApellido());
        if (pago.getCita().getMedico() != null) {
            var medico = pago.getCita().getMedico();
            if (medico.getUsuario() != null) response.setMedicoNombre(medico.getUsuario().getNombre() + " " + medico.getUsuario().getApellido());
            if (medico.getEspecialidad() != null) response.setEspecialidad(medico.getEspecialidad().getNombre());
        }
        if (pago.getCita().getFecha() != null) response.setCitaFechaResumen(pago.getCita().getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        if (pago.getFechaPago() != null) response.setFechaPago(pago.getFechaPago().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (pago.getComprobante() != null) {
            ComprobanteResponse comprobante = new ComprobanteResponse();
            comprobante.setId(pago.getComprobante().getId());
            comprobante.setPagoId(pago.getId());
            comprobante.setNumero(pago.getComprobante().getNumero());
            if (pago.getComprobante().getFecha() != null) comprobante.setFecha(pago.getComprobante().getFecha().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            comprobante.setArchivoUrl(pago.getComprobante().getArchivoUrl());
            response.setComprobante(comprobante);
        }
        return response;
    }
}
