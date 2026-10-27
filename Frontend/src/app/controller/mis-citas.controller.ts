import { CitaService } from '../services/cita.service';
import { UsuarioService } from '../services/usuario.service';
import { MedicoService } from '../services/medico.service';
import { Cita } from '../models/cita.model';
import { UsuarioFull } from '../models/usuario-full.model';
import { Medico } from '../models/medico.model';
import { Pais } from '../models/pais.model';
import { Especialidad } from '../models/especialidad.model';
import { ActivatedRoute, Router } from '@angular/router';
import { HistorialService } from '../services/historial.service';
import { Historial } from '../models/historial.model';
import { NotificationService } from '../services/notification.service';
import { defer, finalize } from 'rxjs';

export class MisCitasController {

  mostrandoModalHistorial = false;
  historialActual: Historial = new Historial();
  vistaSoloLectura = false;
  citaSeleccionada: Cita | null = null;
  historialSeleccionado: Historial | null = null;
  modoVisualizacion = false;

  mostrandoModalCancelar = false;
  citaACancelar: Cita | null = null;
  motivoCancelacion = '';

  modoReprogramacion = false;
  citaEnReprogramacion: Cita | null = null;
  medico: any = null;
  usuario: UsuarioFull = new UsuarioFull();
  usuarioEditado: UsuarioFull = new UsuarioFull();
  listaPaises: Pais[] = [];
  editando = false;
  error = '';
  cargando = false;
  private cargandoCitas = false;
  public fechaActual: Date = new Date();

  citas: Cita[] = [];
  filtroEstado: string = 'TODAS';
  filtroTiempo: string = 'TODAS';
  textoBusqueda: string = '';
  procesandoAccion: boolean = false;

  get citasFiltradas() {
    // Ordenar por fecha descendente, pero si es MEDICO, priorizar las confirmadas
    let lista = [...this.citas].sort((a, b) => {
      if (this.usuario?.rol?.toUpperCase() === 'MEDICO') {
        const aConfirmada = (a.estado === 'CONFIRMADA' || a.estado === 'REPROGRAMADA') ? 1 : 0;
        const bConfirmada = (b.estado === 'CONFIRMADA' || b.estado === 'REPROGRAMADA') ? 1 : 0;
        if (aConfirmada !== bConfirmada) {
          return bConfirmada - aConfirmada;
        }
      }
      return new Date(b.fecha).getTime() - new Date(a.fecha).getTime();
    });

    // Filtro por período de tiempo
    if (this.filtroTiempo !== 'TODAS') {
      const hoy = new Date();
      hoy.setHours(0, 0, 0, 0);

      if (this.filtroTiempo === 'HOY') {
        const finDeHoy = new Date(hoy);
        finDeHoy.setHours(23, 59, 59, 999);
        lista = lista.filter(c => {
          const f = new Date(c.fecha);
          return f >= hoy && f <= finDeHoy;
        });
      } else if (this.filtroTiempo === 'SEMANA') {
        const finSemana = new Date(hoy);
        finSemana.setDate(hoy.getDate() + 6);
        finSemana.setHours(23, 59, 59, 999);
        lista = lista.filter(c => {
          const f = new Date(c.fecha);
          return f >= hoy && f <= finSemana;
        });
      } else if (this.filtroTiempo === 'MES') {
        lista = lista.filter(c => {
          const f = new Date(c.fecha);
          return f.getFullYear() === hoy.getFullYear() && f.getMonth() === hoy.getMonth();
        });
      } else if (this.filtroTiempo === 'PASADAS') {
        lista = lista.filter(c => new Date(c.fecha) < hoy);
      }
    }

    // Filtro por estado
    if (this.filtroEstado !== 'TODAS') {
      lista = lista.filter(c => c.estado === this.filtroEstado);
    }

    // Búsqueda por texto (paciente, médico, especialidad, fecha, ID)
    if (this.textoBusqueda) {
      const b = this.textoBusqueda.toLowerCase();
      lista = lista.filter(c => {
        const fechaStr = c.fecha ? new Date(c.fecha).toLocaleDateString('es-PE') : '';
        return (
          c.paciente?.nombre?.toLowerCase().includes(b) ||
          c.paciente?.apellido?.toLowerCase().includes(b) ||
          c.medico?.usuario?.nombre?.toLowerCase().includes(b) ||
          c.medico?.usuario?.apellido?.toLowerCase().includes(b) ||
          c.medico?.especialidad?.nombre?.toLowerCase().includes(b) ||
          c.id.toString() === b ||
          fechaStr.includes(b)
        );
      });
    }
    return lista;
  }

  mostrandoModalCita = false;
  especialidades: Especialidad[] = [];
  medicosDisponibles: Medico[] = [];
  horariosDisponibles: any[] = []; 
  especialidadSeleccionada: Especialidad | null | undefined = null;
  medicoSeleccionado: Medico | null = null;
  horarioSeleccionado: any = null;
  motivoCita = '';
  fechaSeleccionada: string | null = null;

  mostrarTablaDisponibilidad = false;
  semanaIndice = 0; 
  dias: { nombre: string, fecha: string }[] = [];
  horas = Array.from({ length: 13 }, (_, i) => 8 + i); 
  disponibilidades: any[] = [];

  constructor(
    private citaService: CitaService,
    private usuarioService: UsuarioService,
    private route: ActivatedRoute,
    private router: Router,
    private medicoService: MedicoService,
    private historialService: HistorialService,
    private ns?: NotificationService
  ) { }

  inicializar() {
    const usuarioLocal = localStorage.getItem('usuario');
    const idLocal = usuarioLocal ? JSON.parse(usuarioLocal).id : null;
    const idRuta = Number(this.route.snapshot.paramMap.get('id'));
    const usuarioId = idRuta || idLocal;

    if (usuarioId) {
      console.log(' DOCgosi v2.1.2 - Cargando datos de usuario y disponibilidad...');
      this.cargarUsuario(usuarioId);
      this.cargarEspecialidades();
    } else {
      this.error = 'No se encontró el usuario actual.';
    }
  }

  cargarUsuario(id: number) {
    this.cargando = true;
    this.error = '';
    this.usuarioService.obtenerUsuario(id).subscribe({
      next: data => {
        this.usuario = new UsuarioFull(data);
        this.usuario.pais = new Pais(this.usuario.pais ?? {});
        this.usuarioEditado = new UsuarioFull(this.usuario);

        this.usuarioService.listarPaises().subscribe({
          next: paises => { this.listaPaises = paises ?? []; },
          error: err => console.error('Error al listar países:', err)
        });
        if (this.usuario.rol?.toUpperCase() === 'MEDICO') {
          defer(() => this.medicoService.obtenerPorUsuarioId(this.usuario.id)).subscribe({
            next: medico => { this.medico = medico; this.cargarCitas(); },
            error: err => this.manejarErrorCitas(err)
          });
        } else {
          this.cargarCitas();
        }
      },
      error: err => {
        console.error(err);
        this.error = 'No se pudo cargar el usuario.';
        this.cargando = false;
      }
    });
  }

  activarEdicion() {
    this.editando = true;
    this.usuarioEditado = new UsuarioFull(this.usuario);
  }

  cancelarEdicion() {
    this.editando = false;
    this.usuarioEditado = new UsuarioFull(this.usuario);
  }

  guardarCambios() {
    if (!this.usuarioEditado) return;

    this.usuarioService.actualizarUsuario(this.usuarioEditado).subscribe({
      next: usuarioActualizado => {
        this.usuario = new UsuarioFull(usuarioActualizado);
        this.usuarioEditado = new UsuarioFull(this.usuario);
        this.editando = false;

        const usuarioLocal = JSON.parse(localStorage.getItem('usuario') || '{}');
        if (usuarioLocal.id === this.usuario.id) localStorage.setItem('usuario', JSON.stringify(this.usuario));

        if (this.ns) this.ns.success('Perfil actualizado correctamente');
      },
      error: err => {
        console.error(err);
        this.error = 'No se pudo actualizar el perfil.';
        if (this.ns) this.ns.error('Error al actualizar el perfil');
      }
    });
  }

  comparePais(p1: Pais | null, p2: Pais | null): boolean {
    return !!p1 && !!p2 ? p1.id === p2.id : p1 === p2;
  }

  cargarCitas(showLoading: boolean = true) {
    if (!this.usuario?.id || this.cargandoCitas) return;

    if (showLoading) this.cargando = true;
    const rol = this.usuario.rol?.toUpperCase();
    const idRuta = Number(this.route.snapshot.paramMap.get('id'));

    if (rol === 'MEDICO' && !this.medico?.id) {
      this.error = 'No se encontró el médico asociado para cargar su agenda.';
      this.cargando = false;
      return;
    }
    this.cargandoCitas = true;
    defer(() => {
      if ((rol === 'ADMIN' || rol === 'RECEPCION') && !idRuta) return this.citaService.listarTodas();
      if (rol === 'MEDICO') return this.citaService.listarPorMedico(this.medico.id);
      return this.citaService.listarPorUsuario(this.usuario.id);
    }).pipe(finalize(() => { this.cargandoCitas = false; this.cargando = false; })).subscribe({
      next: data => { this.error = ''; this.asignarCitas(data); },
      error: err => this.manejarErrorCitas(err)
    });
  }

  private asignarCitas(data: any[]) {
    this.citas = data.map(c => {
      c.medico ??= {} as Medico;
      c.medico.usuario ??= {} as UsuarioFull;
      c.medico.especialidad ??= {} as Especialidad;
      c.paciente ??= {} as UsuarioFull;
      (c as any).menuAbierto = false;
      c.tieneHistorial = false; 

      this.historialService.obtenerHistorialPorCita(c.id).subscribe({
        next: (historial) => {
          c.tieneHistorial = !!historial;
        },
        error: (err) => {

          if (err.status !== 404) {
            console.error('Error al obtener historial de la cita', c.id, err);
          }
          c.tieneHistorial = false;
        }
      });

      return c;
    });

    this.cargando = false;
    this.verificarAccionPendiente();
  }

  private verificarAccionPendiente() {
    const idCita = Number(this.route.snapshot.queryParamMap.get('idCita'));
    const accion = this.route.snapshot.queryParamMap.get('accion');

    if (accion === 'nueva-cita') {
      console.log(' Acción detectada: nueva-cita');
      this.abrirModalCita();
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { accion: null },
        queryParamsHandling: 'merge'
      });
      return;
    }

    if (idCita && accion) {
      console.log(' Acción pendiente detectada:', accion, 'para cita:', idCita);
      const cita = this.citas.find(c => c.id === idCita);
      if (cita) {
        if (accion === 'reprogramar') {
          this.reprogramarCita(cita);
        } else if (accion === 'cancelar') {
          this.cancelarCita(cita);
        }

        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { idCita: null, accion: null },
          queryParamsHandling: 'merge'
        });
      }
    }
  }

  private manejarErrorCitas(err: any) {
    console.error('Error al cargar citas:', err);
    this.error = 'Error al cargar citas.';
    this.cargando = false;
  }

  toggleMenu(cita: Cita) {
    this.citas.forEach(c => { if (c !== cita) c.menuAbierto = false; });
    cita.menuAbierto = !cita.menuAbierto;
  }

  reprogramarCita(cita: Cita) {
    this.modoReprogramacion = true;
    this.citaEnReprogramacion = cita;

    this.mostrandoModalCita = true;

    this.especialidadSeleccionada = cita.medico?.especialidad ?? null;
    this.medicoSeleccionado = cita.medico ?? null;
    this.motivoCita = cita.motivo ?? '';

    if (this.medicoSeleccionado && this.medicoSeleccionado.id) {
      this.medicoService.listarHorariosDisponibles(this.medicoSeleccionado.id).subscribe({
        next: (data: any[]) => {
          this.horariosDisponibles = data;
          this.mostrarTablaDisponibilidad = true;
          this.generarDiasConFechas();
        },
        error: (err) => console.error('Error al cargar horarios:', err)
      });
    } else {
      console.warn('No se puede cargar horarios: cita sin médico asignado.');
      this.horariosDisponibles = [];
      this.mostrarTablaDisponibilidad = false;
    }
  }

  abrirModalCita() {
    this.mostrandoModalCita = true;
    this.especialidadSeleccionada = null;
    this.medicoSeleccionado = null;
    this.horarioSeleccionado = null;
    this.medicosDisponibles = [];
    this.horariosDisponibles = [];
    this.motivoCita = '';
    this.mostrarTablaDisponibilidad = false;
    this.semanaIndice = 0;
    this.dias = [];
  }

  cerrarModalCita() {
    this.mostrandoModalCita = false;
  }

  cargarEspecialidades() {
    this.citaService.listarEspecialidades().subscribe({
      next: data => this.especialidades = data,
      error: err => console.error(err)
    });
  }

  cargarMedicosPorEspecialidad() {
    if (!this.especialidadSeleccionada) {
      this.medicosDisponibles = [];
      return;
    }

    const fecha = this.fechaSeleccionada || '';

    this.medicoService.listarPorEspecialidad(
      this.especialidadSeleccionada.id,
      fecha 
    ).subscribe({
      next: data => {
        this.medicosDisponibles = data;
        this.medicoSeleccionado = null;
        this.horariosDisponibles = [];
        this.horarioSeleccionado = null;
        this.mostrarTablaDisponibilidad = false;
      },
      error: err => console.error('Error al cargar médicos:', err)
    });
  }

  cargarHorariosDisponibles() {
    if (!this.medicoSeleccionado) {
      this.horariosDisponibles = [];
      this.mostrarTablaDisponibilidad = false;
      return;
    }

    this.medicoService.listarHorariosDisponibles(this.medicoSeleccionado.id)
      .subscribe({
        next: (data: any[]) => {
          this.horariosDisponibles = data;
          this.horarioSeleccionado = null;
          this.mostrarTablaDisponibilidad = true;
          this.generarDiasConFechas(); 
        },
        error: err => console.error('Error al cargar horarios:', err)
      });
  }

  registrarCita() {
    if (!this.especialidadSeleccionada) {
      if (this.ns) this.ns.error('Debes seleccionar una especialidad.');
      return;
    }

    if (!this.medicoSeleccionado) {
      if (this.ns) this.ns.error('Debes seleccionar a un médico especialista.');
      return;
    }

    if (!this.horarioSeleccionado) {
      if (this.ns) this.ns.error('Debes seleccionar un horario disponible.');
      return;
    }

    if (!this.motivoCita || this.motivoCita.trim() === '') {
      if (this.ns) {
        this.ns.error(this.modoReprogramacion ? 'Por favor, ingresa el motivo del cambio de cita.' : 'Por favor, ingresa el motivo de tu consulta.');
      }
      return;
    }

    this.procesandoAccion = true;

    const fecha = this.horarioSeleccionado.fecha;
    const hora = this.horarioSeleccionado.horaInicio || '00:00:00';
    const fechaCompleta = `${fecha}T${hora}`;

    if (this.modoReprogramacion && this.citaEnReprogramacion) {
      const citaActualizada = {
        ...this.citaEnReprogramacion,
        fecha: fechaCompleta,
        motivo: this.motivoCita
      };

      if (this.usuario.rol === 'PACIENTE') {
        this.citaService.solicitarReprogramar(citaActualizada.id, citaActualizada).subscribe({
          next: () => {
            this.procesandoAccion = false;
            if (this.ns) this.ns.success('Solicitud de reprogramación enviada. Pendiente de aprobación.');
            this.cargarCitas();
            this.cerrarModalCita();
          },
          error: (err) => {
            this.procesandoAccion = false;
            this.procesandoActionError(err, 'Error al solicitar reprogramación');
          }
        });
      } else {
        this.citaService.reprogramarCita(citaActualizada.id, citaActualizada).subscribe({
          next: () => {
            this.procesandoAccion = false;
            if (this.ns) this.ns.success('Cita reprogramada correctamente');
            this.cargarCitas();
            this.cerrarModalCita();
          },
          error: (err) => {
            this.procesandoAccion = false;
            this.procesandoActionError(err, 'Error al reprogramar la cita');
          }
        });
      }

    } else {
      const nuevaCita: any = {
        paciente: this.usuario,
        medico: this.medicoSeleccionado,
        fecha: fechaCompleta,
        motivo: this.motivoCita
      };

      this.citaService.crear(nuevaCita).subscribe({
        next: (citaCreada) => {
          this.procesandoAccion = false;
          if (this.ns) this.ns.success('Reserva realizada. Redirigiendo al pago...');
          this.cargarCitas();
          this.cerrarModalCita();
          if (citaCreada?.id) {
            setTimeout(() => this.router.navigate(['/checkout', citaCreada.id]), 800);
          }
        },
        error: (err) => {
          this.procesandoAccion = false;
          const msg = err.error?.message || 'Error al registrar la cita';
          if (this.ns) this.ns.error(msg);
        }
      });
    }
  }

  confirmarCita(cita: Cita) {
    this.citaService.confirmarCita(cita.id).subscribe({
      next: () => {
        if (this.ns) this.ns.success('Cita confirmada exitosamente');
        this.cargarCitas();
      },
      error: () => { if (this.ns) this.ns.error('Error al confirmar la cita'); }
    });
  }

  aprobarReprogramacion(cita: Cita) {
    if (!cita?.id) return;
    this.procesandoAccion = true;
    this.citaService.confirmarReprogramar(cita.id).subscribe({
      next: () => {
        this.procesandoAccion = false;
        if (this.ns) this.ns.success('Solicitud de reprogramación aprobada con éxito');
        this.cargarCitas();
      },
      error: (err) => { this.procesandoActionError(err, 'Error al aprobar reprogramación'); }
    });
  }

  rechazarReprogramacion(cita: Cita) {
    if (!cita?.id) return;
    this.procesandoAccion = true;
    this.citaService.rechazarReprogramar(cita.id).subscribe({
      next: () => {
        this.procesandoAccion = false;
        if (this.ns) this.ns.success('Solicitud de reprogramación rechazada');
        this.cargarCitas();
      },
      error: (err) => { this.procesandoActionError(err, 'Error al rechazar reprogramación'); }
    });
  }

  cancelarCita(cita: Cita) {
    this.citaACancelar = cita;
    this.motivoCancelacion = '';
    this.mostrandoModalCancelar = true;
  }

  cerrarModalCancelar() {
    this.mostrandoModalCancelar = false;
    this.citaACancelar = null;
  }

  confirmarCancelacionProceso() {
    console.log(' Iniciando proceso de cancelación...', {
      idCita: this.citaACancelar?.id,
      rol: this.usuario?.rol,
      motivo: this.motivoCancelacion
    });

    if (!this.citaACancelar) {
      console.warn('⚠️ No hay cita seleccionada para cancelar.');
      return;
    }

    const rol = this.usuario?.rol?.toUpperCase();

    if (rol === 'PACIENTE' && (!this.motivoCancelacion || this.motivoCancelacion.trim() === '')) {
      if (this.ns) {
        this.ns.error('Por favor, ingresa un motivo para poder procesar la cancelación.');
      }
      return;
    }

    this.procesandoAccion = true;

    if (rol === 'PACIENTE') {
      console.log(' Enviando solicitud de cancelación (Paciente)...');
      this.citaService.solicitarCancelar(this.citaACancelar.id, this.motivoCancelacion).subscribe({
        next: () => {
          console.log(' Solicitud de cancelación enviada con éxito');
          if (this.ns) this.ns.success('Solicitud de cancelación confirmada. Su gestión de reembolso está en proceso de revisión.');
          this.cargarCitas();
          this.cerrarModalCancelar();
          this.procesandoAccion = false;
        },
        error: (err) => {
          console.error('❌ Error al solicitar cancelación:', err);
          this.procesandoActionError(err, 'Error al solicitar cancelación');
        }
      });
    } else {
      console.log(' Confirmando cancelación directa (Admin/Staff)...');
      this.citaService.confirmarCancelar(this.citaACancelar.id).subscribe({
        next: () => {
          console.log(' Cancelación confirmada con éxito');
          if (this.ns) this.ns.success('Cancelación aprobada y reembolso procesado exitosamente.');
          this.cargarCitas();
          this.cerrarModalCancelar();
          this.procesandoAccion = false;
        },
        error: (err) => {
          console.error('❌ Error al confirmar cancelación:', err);
          this.procesandoActionError(err, 'Error al confirmar cancelación e iniciar reembolso');
        }
      });
    }
  }

  private procesandoActionError(err: any, defaultMsg: string) {
    console.error(' Error crítico capturado:', err);
    this.procesandoAccion = false;

    if (err.status === 0) {
      if (this.ns) this.ns.error('Error de conexión con el servidor. Por favor, reintente en unos momentos.');
      return;
    }

    const errorBody = err.error?.error || err.error?.mensaje || err.error || defaultMsg;
    if (this.ns) this.ns.error(errorBody.toString().substring(0, 100));
  }

  rechazarCancelacionProceso() {
    if (!this.citaACancelar) return;
    this.procesandoAccion = true;

    this.citaService.rechazarCancelar(this.citaACancelar.id).subscribe({
      next: () => {
        if (this.ns) this.ns.success('Solicitud de cancelación rechazada. El paciente ha sido notificado.');
        this.cargarCitas();
        this.cerrarModalCancelar();
        this.procesandoAccion = false;
      },
      error: (err) => {
        console.error('Error al rechazar solicitud:', err);
        this.procesandoActionError(err, 'No se pudo procesar el rechazo de la solicitud.');
      }
    });
  }

  generarDiasConFechas(): void {
    const nombresDias = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    const lunesActual = this.getLunesDeSemana();
    const lunesMostrar = new Date(lunesActual);
    lunesMostrar.setDate(lunesActual.getDate() + 7 + this.semanaIndice * 7);

    this.dias = [];
    for (let i = 0; i < 6; i++) {
      const fecha = new Date(lunesMostrar);
      fecha.setDate(lunesMostrar.getDate() + i);

      const nombre = nombresDias[fecha.getDay()];
      const dia = fecha.getDate();
      const mes = fecha.getMonth() + 1;
      const anio = fecha.getFullYear().toString().slice(-2);

      this.dias.push({
        nombre,
        fecha: `${dia}/${mes}/${anio}`
      });
    }
  }

  getLunesDeSemana(): Date {
    const today = new Date();
    const day = today.getDay() || 7;
    const diff = today.getDate() - day + 1;
    return new Date(today.setDate(diff));
  }

  siguienteSemana(): void {
    if (this.semanaIndice < 2) {
      this.semanaIndice++;
      this.generarDiasConFechas();
      this.cargarHorariosDisponibles();
    }
  }

  anteriorSemana(): void {
    if (this.semanaIndice > 0) {
      this.semanaIndice--;
      this.generarDiasConFechas();
      this.cargarHorariosDisponibles();
    }
  }

  isDisponible(fecha: string, hora: number): boolean {
    const fechaISO = this.fechaToISO(fecha);
    const h = this.horariosDisponibles.find(d => {
      const dHora = parseInt(d.horaInicio.split(':')[0], 10);
      return d.fecha === fechaISO && dHora === hora;
    });

    return !!h && h.estado?.toUpperCase() === 'DISPONIBLE';
  }

  isOcupado(fecha: string, hora: number): boolean {
    const fechaISO = this.fechaToISO(fecha);
    const h = this.horariosDisponibles.find(d => {
      const dHora = parseInt(d.horaInicio.split(':')[0], 10);
      return d.fecha === fechaISO && dHora === hora;
    });

    return !!h && h.estado?.toUpperCase() === 'NO_DISPONIBLE';
  }

  private fechaToISO(fecha: string): string {

    const [dia, mes, anio] = fecha.split('/').map(p => parseInt(p, 10));
    const fullAnio = anio < 100 ? 2000 + anio : anio;

    return `${fullAnio}-${mes.toString().padStart(2, '0')}-${dia.toString().padStart(2, '0')}`;
  }

  getHorario(diaFecha: string, hora: number) {
    if (!this.horariosDisponibles) return null;

    const fechaISO = this.fechaToISO(diaFecha);

    return this.horariosDisponibles.find(h => {
      if (!h?.horaInicio) return false;
      const horaInicio = parseInt(h.horaInicio.split(':')[0], 10);
      return h.fecha === fechaISO && horaInicio === hora;
    }) || null;
  }

  seleccionarHorario(diaFecha: string, hora: number) {
    this.horarioSeleccionado = this.getHorario(diaFecha, hora);
    if (this.ns) {
      this.ns.success(`Horario seleccionado (${hora}:00). Por favor, confirma la reserva.`);
    }
  }

  notificarOcupado() {
    if (this.ns) {
      this.ns.error('Este horario ya ha sido reservado por otro paciente.');
    }
  }

  notificarVacio() {
    if (this.ns) {
      this.ns.info('El médico no atiende en este horario.');
    }
  }

  irAGestionarDisponibilidad() {
    if (this.medico && this.medico.id) {
      this.router.navigate(['/gestionar-disponibilidad', this.medico.id]);
    } else {
      if (this.ns) this.ns.error('No se encontró el médico asociado a este usuario.');
    }
  }

  abrirModalHistorial(cita: Cita) {
    this.mostrandoModalHistorial = true;
    this.citaSeleccionada = cita;

    this.historialService.obtenerHistorialPorCita(cita.id).subscribe((data) => {
      if (data) {
        this.historialActual = data;
        this.vistaSoloLectura = true;
        this.modoVisualizacion = true;
        cita.tieneHistorial = true;
      } else {
        this.historialActual = { cita: cita } as Historial;
        this.vistaSoloLectura = false;
        this.modoVisualizacion = false;
        cita.tieneHistorial = false;
      }
    });
  }

  cerrarModalHistorial() {
    this.mostrandoModalHistorial = false;
    this.historialActual = new Historial();
    this.citaSeleccionada = null;
    this.vistaSoloLectura = false;
  }

  guardarHistorial() {
    if (!this.historialActual || !this.historialActual.cita?.id) {
      console.error('No hay cita asociada al historial');
      return;
    }

    const citaId = this.historialActual.cita.id;

    const payload = {
      diagnostico: this.historialActual.diagnostico,
      receta: this.historialActual.receta,
      notas: this.historialActual.notas
    };

    this.historialService.registrarHistorial(citaId, payload).subscribe({
      next: (respuesta) => {
        console.log('Historial registrado correctamente:', respuesta);
        this.mostrandoModalHistorial = false;
        if (this.ns) this.ns.success('Historial médico guardado con éxito.');
        this.cargarCitas();
      },
      error: (err) => {
        console.error('Error al registrar historial:', err);
        if (this.ns) this.ns.error('Error: No se pudo el guardar el diagnóstico.');
      }
    });
  }

  abrirVisualizarHistorial(cita: Cita) {
    this.historialService.obtenerPorCita(cita.id).subscribe({
      next: (historial) => {
        if (historial) {
          this.historialSeleccionado = new Historial(historial);
          this.mostrandoModalHistorial = true;
        }
      },
      error: (err) => {
        console.error('No se encontró historial para esta cita:', err);
        if (this.ns) this.ns.info('No hay diagnóstico ni receta registrados para esta cita actualmente.');
      }
    });
  }

  pagarCita(cita: Cita) {
    this.router.navigate(['/checkout', cita.id]);
  }

  verComprobante(url: string) {
    window.open(url, "_blank", "noopener,noreferrer");
  }
}
