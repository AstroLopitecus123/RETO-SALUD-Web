package com.clinica.real.madrid.backend_citas.service;

import java.math.BigDecimal;

import com.clinica.real.madrid.backend_citas.exception.BadRequestException;
import com.clinica.real.madrid.backend_citas.dto.*;
import com.clinica.real.madrid.backend_citas.model.*;
import com.clinica.real.madrid.backend_citas.repository.CitaRepository;
import com.clinica.real.madrid.backend_citas.repository.PagoRepository;
import com.clinica.real.madrid.backend_citas.repository.UsuarioRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Transactional
    public PaymentIntentResponse crearPaymentIntent(PaymentIntentRequest request) throws StripeException {
        System.out.println("\uD83D\uDCB3 Creando PaymentIntent para Cita ID: " + request.getCitaId() + " - Monto: S/. " + request.getMonto());

        Cita citaParaIntent = citaRepository.findById(request.getCitaId())
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));
        if (request.getUsuarioId() != null && citaParaIntent.getPaciente() != null 
                && !citaParaIntent.getPaciente().getId().equals(request.getUsuarioId())) {
            throw new BadRequestException("No puedes realizar pagos para una cita que pertenece a otro paciente");
        }
        Double precioReal = (citaParaIntent.getMedico() != null && citaParaIntent.getMedico().getEspecialidad() != null && citaParaIntent.getMedico().getEspecialidad().getPrecioBase() != null)
                ? citaParaIntent.getMedico().getEspecialidad().getPrecioBase()
                : (request.getMonto() != null ? request.getMonto() : 0.0);
        long montoCentavos = (long) (precioReal * 100);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(montoCentavos)
                .setCurrency(request.getMoneda() != null ? request.getMoneda().toLowerCase() : "pen")

                .addPaymentMethodType("card")
                .putMetadata("citaId", request.getCitaId().toString())
                .putMetadata("usuarioId", request.getUsuarioId().toString())
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
        System.out.println("\u2705 PaymentIntent creado correctamente: " + intent.getId() + " - Status: " + intent.getStatus());

        return new PaymentIntentResponse(
                intent.getClientSecret(),
                intent.getId(),
                intent.getStatus()
        );
    }

    @Transactional
    public PagoResponse prometerPagoEfectivo(PagoEfectivoRequest request) {
        Cita cita = citaRepository.findById(request.getCitaId())
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (cita.getPaciente() != null && !cita.getPaciente().getId().equals(usuario.getId())) {
            throw new BadRequestException("No puedes pagar una cita que pertenece a otro paciente");
        }

        Pago pago = new Pago();
        pago.setCita(cita);
        pago.setUsuario(usuario);
        BigDecimal precioEfectivo = (cita.getMedico() != null && cita.getMedico().getEspecialidad() != null && cita.getMedico().getEspecialidad().getPrecioBase() != null)
                ? BigDecimal.valueOf(cita.getMedico().getEspecialidad().getPrecioBase())
                : request.getMonto();
        pago.setMonto(precioEfectivo);
        pago.setMetodo(MetodoPago.EFECTIVO);
        pago.setEstadoPago(EstadoPago.PENDIENTE); 
        pago.setFechaPago(null);

        Pago savedPago = pagoRepository.save(pago);

        return mapToResponse(savedPago);
    }

    @Transactional
    public PagoResponse completarPago(Long pagoId) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));

        pago.setEstadoPago(EstadoPago.COMPLETADO);
        pago.setFechaPago(LocalDateTime.now(java.time.ZoneId.systemDefault()));

        if (pago.getComprobante() == null) {
            Comprobante comprobante = new Comprobante();
            comprobante.setNumero("FACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            comprobante.setFecha(LocalDateTime.now(java.time.ZoneId.systemDefault()));
            comprobante.setPago(pago);
            pago.setComprobante(comprobante);
        }

        if (pago.getCita() != null) {
            pago.getCita().setEstado(EstadoCita.CONFIRMADA);
            citaRepository.save(pago.getCita());
        }

        Pago savedPago = pagoRepository.save(pago);
        return mapToResponse(savedPago);
    }

    @Transactional
    public PagoResponse pagarEfectivo(PagoEfectivoRequest request) {
        Cita cita = citaRepository.findById(request.getCitaId())
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (cita.getPaciente() != null && !cita.getPaciente().getId().equals(usuario.getId())) {
            throw new BadRequestException("No puedes pagar una cita que pertenece a otro paciente");
        }

        Pago pago = new Pago();
        pago.setCita(cita);
        pago.setUsuario(usuario);
        BigDecimal precioEfectivo = (cita.getMedico() != null && cita.getMedico().getEspecialidad() != null && cita.getMedico().getEspecialidad().getPrecioBase() != null)
                ? BigDecimal.valueOf(cita.getMedico().getEspecialidad().getPrecioBase())
                : request.getMonto();
        pago.setMonto(precioEfectivo);
        pago.setMetodo(MetodoPago.EFECTIVO);
        pago.setEstadoPago(EstadoPago.COMPLETADO);
        pago.setFechaPago(LocalDateTime.now(java.time.ZoneId.systemDefault()));

        Comprobante comprobante = new Comprobante();
        comprobante.setNumero("FACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        comprobante.setFecha(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        comprobante.setPago(pago);

        pago.setComprobante(comprobante);
        Pago savedPago = pagoRepository.save(pago);

        cita.setEstado(EstadoCita.CONFIRMADA);
        citaRepository.save(cita);

        return mapToResponse(savedPago);
    }

    @Transactional
    public PagoResponse pagarTarjeta(PagoTarjetaRequest request) {
        Cita cita = citaRepository.findById(request.getCitaId())
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));
        Usuario usuario = usuarioRepository.findById(request.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (cita.getPaciente() != null && !cita.getPaciente().getId().equals(usuario.getId())) {
            throw new BadRequestException("No puedes pagar una cita que pertenece a otro paciente");
        }

        Pago pago = new Pago();
        pago.setCita(cita);
        pago.setUsuario(usuario);
        BigDecimal precioTarjeta = (cita.getMedico() != null && cita.getMedico().getEspecialidad() != null && cita.getMedico().getEspecialidad().getPrecioBase() != null)
                ? BigDecimal.valueOf(cita.getMedico().getEspecialidad().getPrecioBase())
                : request.getMonto();
        pago.setMonto(precioTarjeta);
        pago.setMetodo(MetodoPago.TARJETA);
        pago.setTransaccionId(request.getReferencia());
        pago.setFechaPago(LocalDateTime.now(java.time.ZoneId.systemDefault()));

                if (Boolean.TRUE.equals(request.getExito())) {
            if (request.getReferencia() != null && !request.getReferencia().trim().isEmpty() && pagoRepository.existsByTransaccionId(request.getReferencia().trim())) {
                throw new BadRequestException("Esta transaccion de pago ya fue procesada anteriormente");
            }
            // Validacion obligatoria con Stripe
            if (stripeSecretKey != null && stripeSecretKey.startsWith("sk_")) {
                if (request.getReferencia() == null || request.getReferencia().trim().isEmpty()) {
                    throw new BadRequestException("Referencia de transaccion de Stripe requerida");
                }
                try {
                    PaymentIntent intent = PaymentIntent.retrieve(request.getReferencia());
                    if (!"succeeded".equalsIgnoreCase(intent.getStatus())) {
                        throw new BadRequestException("El pago no ha sido completado en Stripe. Estado: " + intent.getStatus());
                    }
                    long montoEsperadoCentavos = (long) (precioTarjeta.doubleValue() * 100);
                    if (intent.getAmount() == null || intent.getAmount() < montoEsperadoCentavos) {
                        throw new BadRequestException("El monto pagado en Stripe no coincide con el precio de la cita");
                    }
                    if (intent.getMetadata() != null && intent.getMetadata().containsKey("citaId")) {
                        if (!intent.getMetadata().get("citaId").equals(cita.getId().toString())) {
                            throw new BadRequestException("El pago no corresponde a esta cita");
                        }
                    }
                } catch (StripeException e) {
                    throw new BadRequestException("Error al validar el pago con Stripe: " + e.getMessage());
                }
            } else {
                throw new BadRequestException("La pasarela de pago Stripe no esta configurada correctamente en el servidor.");
            }

            pago.setEstadoPago(EstadoPago.COMPLETADO);

            Comprobante comprobante = new Comprobante();
            comprobante.setNumero("FACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            comprobante.setFecha(LocalDateTime.now(java.time.ZoneId.systemDefault()));
            comprobante.setPago(pago);
            pago.setComprobante(comprobante);

            cita.setEstado(EstadoCita.CONFIRMADA);
            citaRepository.save(cita);
        } else {
            pago.setEstadoPago(EstadoPago.PENDIENTE);
        }

        Pago savedPago = pagoRepository.save(pago);
        return mapToResponse(savedPago);
    }

    public List<PagoResponse> obtenerPagosPorCita(Long citaId) {
        return pagoRepository.findByCitaId(citaId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<PagoResponse> obtenerTodosLosPagos() {
        return pagoRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<PagoResponse> obtenerPagosPorUsuario(Long usuarioId) {
        return pagoRepository.findByUsuarioId(usuarioId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<PagoResponse> obtenerPagosPorMedico(Long medicoId) {
        return pagoRepository.findByCita_Medico_Id(medicoId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PagoResponse anularPago(Long pagoId) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
        pago.setEstadoPago(EstadoPago.ANULADO);
        Pago savedPago = pagoRepository.save(pago);
        return mapToResponse(savedPago);
    }

    @Transactional
    public void reembolsarPago(Long citaId) {
        List<Pago> pagos = pagoRepository.findByCitaId(citaId);
        if (pagos.isEmpty()) {
            System.out.println("âš ï¸ No se encontraron pagos para reembolsar en la cita #" + citaId);
            return;
        }

        for (Pago pago : pagos) {
            if (pago.getEstadoPago() == EstadoPago.REEMBOLSADO || pago.getEstadoPago() == EstadoPago.ANULADO) continue;

            if (pago.getMetodo() == MetodoPago.TARJETA && pago.getTransaccionId() != null) {
                try {
                    RefundCreateParams params = RefundCreateParams.builder()
                            .setPaymentIntent(pago.getTransaccionId())
                            .build();
                    Refund refund = Refund.create(params);
                    System.out.println(" Reembolso exitoso en Stripe. ID: " + refund.getId());
                    pago.setEstadoPago(EstadoPago.REEMBOLSADO);
                } catch (StripeException e) {
                    System.err.println("âŒ Error de Stripe al reembolsar pago #" + pago.getId() + ": " + e.getMessage());

                }
            } else {

                pago.setEstadoPago(EstadoPago.ANULADO);
            }
            pagoRepository.save(pago);
        }
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

        if (pago.getUsuario() != null) {
            response.setPacienteNombre(pago.getUsuario().getNombre() + " " + pago.getUsuario().getApellido());
        }
        if (pago.getCita() != null && pago.getCita().getMedico() != null) {
            response.setMedicoNombre(pago.getCita().getMedico().getUsuario().getNombre() + " " + pago.getCita().getMedico().getUsuario().getApellido());
            if (pago.getCita().getMedico().getEspecialidad() != null) {
                response.setEspecialidad(pago.getCita().getMedico().getEspecialidad().getNombre());
            }
            if (pago.getCita().getFecha() != null) {
                response.setCitaFechaResumen(pago.getCita().getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            }
        }

        if (pago.getFechaPago() != null) {
            response.setFechaPago(pago.getFechaPago().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        if (pago.getComprobante() != null) {
            ComprobanteResponse cRes = new ComprobanteResponse();
            cRes.setId(pago.getComprobante().getId());
            cRes.setPagoId(pago.getId());
            cRes.setNumero(pago.getComprobante().getNumero());
            if (pago.getComprobante().getFecha() != null) {
                cRes.setFecha(pago.getComprobante().getFecha().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            cRes.setArchivoUrl(pago.getComprobante().getArchivoUrl());
            response.setComprobante(cRes);
        }

        return response;
    }
}
