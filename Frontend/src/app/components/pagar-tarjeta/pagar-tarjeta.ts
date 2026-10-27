import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CitaService } from '../../services/cita.service';
import { PagoService } from '../../services/pago.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-pagar-tarjeta',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './pagar-tarjeta.html',
  styleUrls: ['./pagar-tarjeta.css']
})
export class PagarTarjetaComponent implements OnInit, AfterViewInit, OnDestroy {
  citaId: number | null = null;
  cita: any = null;
  usuario: any = null;

  cargando = false;
  pagoExitoso = false;
  cardElementMontado = false;
  nombreTitular = '';

  cardBrand = 'unknown';
  cardLast4 = '••••';
  tarjetaCompleta = false;
  errorTarjeta = '';

  monto = 100.00;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private citaService: CitaService,
    private pagoService: PagoService,
    private ns: NotificationService
  ) {}

  async ngOnInit(): Promise<void> {
    this.citaId = Number(this.route.snapshot.paramMap.get('id'));
    const usrStr = localStorage.getItem('usuario');
    if (usrStr) {
      this.usuario = JSON.parse(usrStr);
      this.nombreTitular = `${this.usuario.nombre || ''} ${this.usuario.apellido || ''}`.trim();
    }

    if (this.citaId) {
      this.citaService.obtenerPorId(this.citaId).subscribe({
        next: (data: any) => {
          this.cita = data;
          if (data?.medico?.especialidad?.precioBase) {
            this.monto = data.medico.especialidad.precioBase;
          }
        },
        error: (err: any) => console.error('Error cargando cita:', err)
      });
    } else {
      this.router.navigate(['/']);
    }
  }

  async ngAfterViewInit(): Promise<void> {

    setTimeout(async () => {
      console.log('💳 Iniciando Stripe Card Element...');
      const element = await this.pagoService.createCardElement('stripe-card-element');
      if (element) {
        this.cardElementMontado = true;
        console.log(' Stripe Card Element listo para recibir datos de tarjeta.');

        element.on('change', (event: any) => {
          this.cardBrand = event.brand || 'unknown';
          this.tarjetaCompleta = event.complete;

          if (event.error) {
            this.errorTarjeta = this.traducirErrorStripe(event.error);
          } else {
            this.errorTarjeta = '';
          }
        });

      } else {
        console.error('❌ No se pudo montar el formulario de Stripe.');
        this.ns.error('Error al cargar el formulario seguro de pago. Recarga la página.');
      }
    }, 400);
  }

  ngOnDestroy(): void {
    this.pagoService.destroyCardElement();
  }

  async procesarPago(): Promise<void> {
    if (this.cargando || !this.cardElementMontado) return;
    if (!this.tarjetaCompleta) {
      this.ns.error('Por favor completa todos los datos de la tarjeta correctamente.');
      return;
    }
    
    const nombre = this.nombreTitular.trim();
    if (!nombre) {
      this.ns.error('Por favor escribe tu nombre completo.');
      return;
    }

    if (nombre.length < 2 || nombre.length > 50) {
      this.ns.error('El nombre del titular debe tener entre 2 y 50 caracteres.');
      return;
    }

    if (!/^[A-Za-zÁÉÍÓÚáéíóúÑñ ]+$/.test(nombre)) {
      this.ns.error('El nombre del titular solo puede contener letras y espacios.');
      return;
    }

    this.cargando = true;
    console.log('💳 Iniciando proceso de pago real con Stripe...');

    if (!this.citaId) {
      this.ns.error('ID de cita no encontrado.');
      this.cargando = false;
      return;
    }

    this.pagoService.crearPaymentIntent({
      citaId: this.citaId,
      usuarioId: this.usuario?.id,
      monto: this.monto,
      moneda: 'pen'
    }).subscribe({
      next: async (res: any) => {
        let paymentIntentId = res.id;
        if (res.status !== 'succeeded') {
          const clientSecret = res.clientSecret;
          if (!clientSecret) {
            this.ns.error('Error: No se recibió el token de pago del servidor.');
            this.cargando = false;
            return;
          }

          console.log(' PaymentIntent creado. Confirmando con Stripe...');

          let resultado;
          try {
            resultado = await this.pagoService.confirmarPagoConTarjeta(
              clientSecret,
              this.usuario?.correo || '',
              this.nombreTitular
            );
          } catch (err) {
            this.cargando = false;
            this.pagoExitoso = false;
            this.ns.error('No se pudo confirmar el pago con Stripe. Reintenta para verificar su estado.');
            console.error('Error al confirmar el pago con Stripe:', err);
            return;
          }

          if (!resultado.success) {
            this.cargando = false;
            const mensajeReal = this.getMensajeErrorReal(resultado);
            this.ns.error(mensajeReal);
            return;
          }
          paymentIntentId = resultado.paymentIntentId;
        }

        if (!paymentIntentId) {
          this.cargando = false;
          this.ns.error('Error: No se recibió el ID del pago confirmado.');
          return;
        }

        console.log('🎉 ¡Stripe confirmó el pago! Notificando al sistema...');

        const body = {
          citaId: this.citaId as number,
          usuarioId: this.usuario?.id,
          monto: this.monto,
          referencia: paymentIntentId,
          exito: true
        };

        this.pagoService.pagarTarjeta(body).subscribe({
          next: () => {
            this.cargando = false;
            this.pagoExitoso = true;
            this.ns.success('¡Pago procesado exitosamente con Stripe!');
          },
          error: (err: any) => {
            this.cargando = false;

            this.pagoExitoso = false;
            this.ns.error('El cobro se procesó en Stripe pero hubo un problema al confirmar en el sistema. Contacta a soporte con el ID: ' + paymentIntentId);
            console.error('Error al notificar backend post-pago:', err);
          }
        });
      },
      error: (err: any) => {
        this.cargando = false;
        this.ns.error('Error al conectar con el servidor de pagos.');
        console.error('Error al crear PaymentIntent:', err);
      }
    });
  }

  volver(): void {
    if (this.citaId) {
      this.router.navigate(['/checkout', this.citaId]);
    } else {
      this.router.navigate(['/paciente/dashboard']);
    }
  }

  private getMensajeErrorReal(resultado: any): string {
    const code = resultado.errorCode;

    const mensajes: { [key: string]: string } = {
      'test_mode_live_card': 'La tarjeta ingresada no puede ser procesada en este entorno. Verifique los datos o use otra tarjeta.',
      'card_declined': 'Su tarjeta ha sido rechazada por la entidad emisora. Por favor, contacte con su banco.',
      'expired_card': 'La tarjeta ha caducado. Por favor, verifique la fecha de vencimiento e intente de nuevo.',
      'incorrect_cvc': 'El código de seguridad (CVC) es incorrecto. Revise el reverso de su tarjeta.',
      'insufficient_funds': 'La transacción ha fallado por fondos insuficientes en la cuenta.',
      'incorrect_number': 'El número de tarjeta es incorrecto o incompleto.',
      'processing_error': 'Ha ocurrido un error técnico al procesar el pago. Por favor, intente de nuevo en unos minutos.'
    };

    return mensajes[code] || resultado.error || 'No se pudo procesar el pago. Verifique sus datos e intente de nuevo.';
  }

  private traducirErrorStripe(error: any): string {
    const code = error.code;
    switch(code) {
      case 'incomplete_number': return 'El número de tarjeta está incompleto.';
      case 'incomplete_cvc': return 'El código CVC está incompleto (deben ser 3 o 4 dígitos).';
      case 'incomplete_expiry': return 'La fecha de vencimiento está incompleta.';
      case 'invalid_number': return 'El número de tarjeta es inválido.';
      case 'invalid_expiry_year_past': return 'El año de vencimiento está en el pasado.';
      case 'invalid_cvc': return 'El código CVC es inválido.';
      case 'incorrect_number': return 'El número de tarjeta es incorrecto.';
      default: return error.message || 'Datos de la tarjeta inválidos.';
    }
  }
}
