import { of, throwError } from 'rxjs';
import { HistorialMedicoComponent } from './historial-medico';
import { HistorialService } from '../../services/historial.service';
import { ActivatedRoute } from '@angular/router';
import { NotificationService } from '../../services/notification.service';

describe('HistorialMedicoComponent loading', () => {
  it('clears load errors after a successful empty retry', () => {
    const service = jasmine.createSpyObj<HistorialService>('HistorialService', ['obtenerHistorialPorPaciente']);
    const component = new HistorialMedicoComponent(service, {} as ActivatedRoute, {} as NotificationService);
    component.usuario = { id: 1, rol: 'PACIENTE' };
    service.obtenerHistorialPorPaciente.and.returnValue(throwError(() => new Error('offline')));
    component.cargarHistorial();
    expect(component.error).not.toBe('');
    expect(component.cargando).toBeFalse();
    service.obtenerHistorialPorPaciente.and.returnValue(of([]));
    component.cargarHistorial();
    expect(component.error).toBe('');
    expect(component.historialesFiltrados).toEqual([]);
  });
});
