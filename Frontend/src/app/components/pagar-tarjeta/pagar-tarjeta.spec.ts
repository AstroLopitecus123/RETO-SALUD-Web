import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { CitaService } from '../../services/cita.service';
import { PagoService } from '../../services/pago.service';
import { NotificationService } from '../../services/notification.service';
import { PagarTarjetaComponent } from './pagar-tarjeta';

describe('PagarTarjetaComponent', () => {
  let component: PagarTarjetaComponent;
  let pagos: jasmine.SpyObj<PagoService>;
  let ns: jasmine.SpyObj<NotificationService>;

  async function waitUntil(condition: () => boolean): Promise<void> {
    for (let attempt = 0; attempt < 20; attempt++) {
      if (condition()) return;
      await Promise.resolve();
    }
    throw new Error('El callback asincrono de pago no alcanzo el estado esperado.');
  }

  beforeEach(() => {
    pagos = jasmine.createSpyObj<PagoService>('PagoService', [
      'crearPaymentIntent', 'confirmarPagoConTarjeta', 'pagarTarjeta'
    ]);
    ns = jasmine.createSpyObj<NotificationService>('NotificationService', ['error', 'success']);
    component = new PagarTarjetaComponent(
      {} as ActivatedRoute, {} as Router, {} as CitaService, pagos, ns
    );
    component.citaId = 7;
    component.usuario = { id: 3, correo: 'ana@example.com' };
    component.nombreTitular = 'Ana Perez';
    component.cardElementMontado = true;
    component.tarjetaCompleta = true;
    pagos.crearPaymentIntent.and.returnValue(of({
      id: 'pi_nuevo', clientSecret: 'secret', status: 'requires_payment_method'
    }));
    pagos.confirmarPagoConTarjeta.and.resolveTo({ success: true, paymentIntentId: 'pi_nuevo' });
    pagos.pagarTarjeta.and.returnValue(of({} as any));
  });

  it('registra un intento succeeded sin token ni otra confirmacion de tarjeta', async () => {
    pagos.crearPaymentIntent.and.returnValue(of({ id: 'pi_cobrado', status: 'succeeded' }));
    const registro = new Subject<any>();
    pagos.pagarTarjeta.and.returnValue(registro);

    void component.procesarPago();
    await waitUntil(() => pagos.pagarTarjeta.calls.count() === 1);

    expect(pagos.confirmarPagoConTarjeta).not.toHaveBeenCalled();
    expect(pagos.pagarTarjeta).toHaveBeenCalledOnceWith({
      citaId: 7, usuarioId: 3, monto: 100, referencia: 'pi_cobrado', exito: true
    });
    expect(component.cargando).toBeTrue();
    expect(component.pagoExitoso).toBeFalse();
    expect(ns.success).not.toHaveBeenCalled();
    await component.procesarPago();
    expect(pagos.crearPaymentIntent).toHaveBeenCalledTimes(1);

    registro.next({});
    registro.complete();
    expect(component.cargando).toBeFalse();
    expect(component.pagoExitoso).toBeTrue();
    expect(ns.success).toHaveBeenCalledTimes(1);
  });

  it('mantiene el primer pago y confirma con Stripe antes del registro local', async () => {
    void component.procesarPago();
    expect(pagos.crearPaymentIntent).toHaveBeenCalledOnceWith({
      citaId: 7, usuarioId: 3, monto: 100, moneda: 'pen'
    });
    expect(pagos.confirmarPagoConTarjeta).toHaveBeenCalledOnceWith('secret', 'ana@example.com', 'Ana Perez');
    expect(pagos.pagarTarjeta).not.toHaveBeenCalled();
    await waitUntil(() => !component.cargando);
    expect(pagos.pagarTarjeta).toHaveBeenCalledOnceWith({
      citaId: 7, usuarioId: 3, monto: 100, referencia: 'pi_nuevo', exito: true
    });
    expect(component.cargando).toBeFalse();
    expect(component.pagoExitoso).toBeTrue();
  });

  it('maneja el rechazo de la Promise sin registrar ni anunciar exito', async () => {
    pagos.confirmarPagoConTarjeta.and.callFake(() => Promise.reject(new Error('offline')));
    void component.procesarPago();
    await waitUntil(() => !component.cargando);
    expect(component.cargando).toBeFalse();
    expect(component.pagoExitoso).toBeFalse();
    expect(pagos.pagarTarjeta).not.toHaveBeenCalled();
    expect(ns.success).not.toHaveBeenCalled();
    expect(ns.error).toHaveBeenCalledTimes(1);
  });

  it('permite recuperar el registro local fallido sin volver a cobrar', async () => {
    pagos.crearPaymentIntent.and.returnValue(of({ id: 'pi_cobrado', status: 'succeeded' }));
    pagos.pagarTarjeta.and.returnValue(throwError(() => new Error('offline')));
    void component.procesarPago();
    await waitUntil(() => !component.cargando);
    expect(component.cargando).toBeFalse();
    expect(component.pagoExitoso).toBeFalse();
    expect(ns.success).not.toHaveBeenCalled();
    expect(ns.error).toHaveBeenCalledWith(jasmine.stringMatching('pi_cobrado'));

    pagos.pagarTarjeta.and.returnValue(of({} as any));
    void component.procesarPago();
    await waitUntil(() => !component.cargando);
    expect(pagos.confirmarPagoConTarjeta).not.toHaveBeenCalled();
    expect(pagos.pagarTarjeta).toHaveBeenCalledTimes(2);
    expect(component.pagoExitoso).toBeTrue();
  });

  it('mantiene el error de tarjeta rechazada sin registro local', async () => {
    pagos.confirmarPagoConTarjeta.and.resolveTo({ success: false, errorCode: 'card_declined' });
    void component.procesarPago();
    await waitUntil(() => !component.cargando);
    expect(component.cargando).toBeFalse();
    expect(component.pagoExitoso).toBeFalse();
    expect(pagos.pagarTarjeta).not.toHaveBeenCalled();
    expect(ns.success).not.toHaveBeenCalled();
    expect(ns.error).toHaveBeenCalledWith(jasmine.stringMatching('rechazada'));
  });

  it('rechaza succeeded sin ID sin confirmar otra vez la tarjeta', async () => {
    pagos.crearPaymentIntent.and.returnValue(of({ status: 'succeeded', clientSecret: 'secret' }));
    void component.procesarPago();
    await waitUntil(() => !component.cargando);
    expect(component.cargando).toBeFalse();
    expect(component.pagoExitoso).toBeFalse();
    expect(pagos.confirmarPagoConTarjeta).not.toHaveBeenCalled();
    expect(pagos.pagarTarjeta).not.toHaveBeenCalled();
    expect(ns.success).not.toHaveBeenCalled();
    expect(ns.error).toHaveBeenCalled();
  });

  it('maneja un error al crear el intento sin confirmar ni registrar', async () => {
    pagos.crearPaymentIntent.and.returnValue(throwError(() => new Error('offline')));
    void component.procesarPago();
    await waitUntil(() => !component.cargando);
    expect(component.cargando).toBeFalse();
    expect(component.pagoExitoso).toBeFalse();
    expect(pagos.confirmarPagoConTarjeta).not.toHaveBeenCalled();
    expect(pagos.pagarTarjeta).not.toHaveBeenCalled();
    expect(ns.success).not.toHaveBeenCalled();
    expect(ns.error).toHaveBeenCalled();
  });
});
