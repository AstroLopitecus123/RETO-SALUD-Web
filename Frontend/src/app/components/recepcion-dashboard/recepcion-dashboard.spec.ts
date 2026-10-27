import { of, Subject, throwError } from 'rxjs';
import { RecepcionDashboardComponent } from './recepcion-dashboard';
import { CitaService } from '../../services/cita.service';
import { MedicoService } from '../../services/medico.service';
import { Cita } from '../../models/cita.model';
import { ListaUsuariosController } from '../../controller/lista-usuarios.controller';
import { UsuarioFull } from '../../models/usuario-full.model';
import { UsuarioService } from '../../services/usuario.service';
import { Router, ActivatedRoute } from '@angular/router';
import { NotificationService } from '../../services/notification.service';
import { MisCitasController } from '../../controller/mis-citas.controller';
import { HistorialService } from '../../services/historial.service';

describe('Reception and appointment logic regressions', () => {
  it('calculates real metrics and recovers after a failed load', () => {
    const citas = jasmine.createSpyObj<CitaService>('CitaService', ['listarTodas']);
    const medicos = jasmine.createSpyObj<MedicoService>('MedicoService', ['listarMedicos']);
    const component = new RecepcionDashboardComponent(citas, medicos);
    citas.listarTodas.and.returnValue(throwError(() => new Error('offline')));
    medicos.listarMedicos.and.returnValue(of([]));
    component.cargarDatos();
    expect(component.error).not.toBe('');
    citas.listarTodas.and.returnValue(of([
      { fecha: new Date().toISOString(), estado: 'PENDIENTE' },
      { fecha: '2000-01-01T12:00:00', estado: 'CONFIRMADA' }
    ] as Cita[]));
    medicos.listarMedicos.and.returnValue(of([new UsuarioFull()]));
    component.cargarDatos();
    expect(component.error).toBe('');
    expect(component.stats).toEqual({ citasHoy: 1, pendientesConfirmar: 1, medicosRegistrados: 1 });
  });

  it('clamps the current users page when the last page disappears', () => {
    const controller = new ListaUsuariosController({} as UsuarioService, {} as Router, {} as NotificationService);
    controller.usuariosFiltrados = [new UsuarioFull()];
    controller.paginaActual = 3;
    controller.actualizarPagina();
    expect(controller.paginaActual).toBe(1);
    expect(controller.usuariosPaginados[0]).toBe(controller.usuariosFiltrados[0]);
    controller.usuariosFiltrados = [];
    controller.actualizarPagina();
    expect(controller.totalPaginas).toBe(1);
    expect(controller.paginaActual).toBe(1);
  });

  it('keeps loading until appointments arrive even if country loading fails', () => {
    const users = jasmine.createSpyObj<UsuarioService>('UsuarioService', ['obtenerUsuario', 'listarPaises']);
    const citas = jasmine.createSpyObj<CitaService>('CitaService', ['listarPorUsuario']);
    const response = new Subject<Cita[]>();
    users.obtenerUsuario.and.returnValue(of(new UsuarioFull({ id: 1, rol: 'PACIENTE' })));
    users.listarPaises.and.returnValue(throwError(() => new Error('offline')));
    citas.listarPorUsuario.and.returnValue(response);
    const route = { snapshot: { paramMap: { get: () => null }, queryParamMap: { get: () => null } } } as unknown as ActivatedRoute;
    const controller = new MisCitasController(citas, users, route, {} as Router, {} as MedicoService, {} as HistorialService);
    controller.cargarUsuario(1);
    expect(controller.cargando).toBeTrue();
    controller.cargarCitas(false);
    expect(citas.listarPorUsuario).toHaveBeenCalledTimes(1);
    response.next([]); response.complete();
    expect(controller.cargando).toBeFalse();
    expect(controller.error).toBe('');
  });
});
