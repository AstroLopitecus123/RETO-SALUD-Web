import { of, Subject, throwError } from 'rxjs';
import { MisPagosComponent } from './mis-pagos';
import { PagoService } from '../../services/pago.service';
import { NotificationService } from '../../services/notification.service';
import { Pago } from '../../models/pago.model';

describe('MisPagosComponent loading', () => {
  it('prevents overlapping polling and clears errors after recovery', () => {
    const service = jasmine.createSpyObj<PagoService>('PagoService', ['obtenerPagosPorUsuario']);
    const component = new MisPagosComponent(service, {} as NotificationService);
    component.usuario = { id: 1, rol: 'PACIENTE' };
    service.obtenerPagosPorUsuario.and.returnValue(throwError(() => new Error('offline')));
    component.cargarPagos();
    expect(component.error).not.toBe('');
    const response = new Subject<Pago[]>();
    service.obtenerPagosPorUsuario.and.returnValue(response);
    component.cargarPagos();
    component.cargarPagos(false);
    expect(service.obtenerPagosPorUsuario).toHaveBeenCalledTimes(2);
    expect(component.cargando).toBeTrue();
    response.next([]); response.complete();
    expect(component.error).toBe('');
    expect(component.cargando).toBeFalse();
    expect(component.totalTransacciones).toBe(0);
  });
});
