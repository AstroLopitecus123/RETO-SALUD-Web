import { Subject, of, throwError } from 'rxjs';
import { NotificacionesComponent } from './notificaciones';
import { Notificacion, NotificacionService } from '../../services/notificacion.service';
import { NotificationService } from '../../services/notification.service';
import { Router } from '@angular/router';

describe('NotificacionesComponent logic', () => {
  let service: jasmine.SpyObj<NotificacionService>;
  let ns: jasmine.SpyObj<NotificationService>;
  let component: NotificacionesComponent;
  const notification = (id: number): Notificacion => ({
    id, titulo: 'Cita', mensaje: 'Detalle', fechaCreacion: '2026-10-26', leida: false, rolDestino: 'PACIENTE'
  });

  beforeEach(() => {
    service = jasmine.createSpyObj('NotificacionService', ['getMisNotificaciones', 'marcarComoLeida', 'notificarCambio']);
    ns = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    component = new NotificacionesComponent(service, ns, {} as Router);
    component.loading = false;
  });

  it('waits for every request, blocks duplicate batches and never opens a modal', () => {
    const first = new Subject<Notificacion>();
    const second = new Subject<Notificacion>();
    component.notificaciones = [notification(1), notification(2)];
    service.marcarComoLeida.and.returnValues(first, second);
    component.marcarTodasComoLeidas();
    component.marcarTodasComoLeidas();
    expect(service.marcarComoLeida).toHaveBeenCalledTimes(2);
    first.next(notification(1)); first.complete();
    expect(component.marcandoTodas).toBeTrue();
    expect(service.notificarCambio).not.toHaveBeenCalled();
    second.next(notification(2)); second.complete();
    expect(component.marcandoTodas).toBeFalse();
    expect(component.getCountNoLeidas()).toBe(0);
    expect(service.notificarCambio).toHaveBeenCalledTimes(1);
    expect(component.mostrarModal).toBeFalse();
    expect(ns.success).not.toHaveBeenCalled();
    expect(ns.error).not.toHaveBeenCalled();
  });

  it('preserves partial success and retries only failed notifications', () => {
    component.notificaciones = [notification(1), notification(2)];
    service.marcarComoLeida.and.returnValues(of(notification(1)), throwError(() => new Error('offline')));
    component.marcarTodasComoLeidas();
    expect(component.notificaciones.map(n => n.leida)).toEqual([true, false]);
    expect(component.errorAccion).not.toBe('');
    service.marcarComoLeida.and.returnValue(of(notification(2)));
    component.marcarTodasComoLeidas();
    expect(service.marcarComoLeida.calls.mostRecent().args).toEqual([2]);
    expect(component.errorAccion).toBe('');
  });

  it('distinguishes a failed load from an empty successful response', () => {
    service.getMisNotificaciones.and.returnValue(throwError(() => new Error('offline')));
    component.cargarNotificaciones();
    expect(component.loading).toBeFalse();
    expect(component.error).not.toBe('');
    service.getMisNotificaciones.and.returnValue(of([]));
    component.cargarNotificaciones();
    expect(component.error).toBe('');
    expect(component.notificaciones).toEqual([]);
  });
});
