import { Component, OnInit } from '@angular/core';
import { Medico } from '../../models/medico.model';
import { Disponibilidad } from '../../models/disponibilidad.model';
import { EstadoDisponibilidad } from '../../models/tipos';
import { GestionarDisponibilidadController } from '../../controller/gestionar-disponibilidad.controller';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MedicoService } from '../../services/medico.service';
import { Router } from '@angular/router';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-gestionar-disponibilidad',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './gestionar-disponibilidad.html',
  styleUrls: ['./gestionar-disponibilidad.css']
})
export class GestionarDisponibilidadComponent implements OnInit {

  minimoHorasSemana = 36;
  minimoHorasDia = 6;
  semanaIndice = 0; 
  medico!: Medico;
  disponibilidades: Disponibilidad[] = [];

  mostrarModalConfirmacion = false;
  mostrarModalBloqueo = false;
  mensajeBloqueo = '';

  resultadoValidacion = {
    totalValido: true,
    distribucionValida: true,
    diasBajos: [] as string[] 
  };

  dias: { nombre: string, fecha: string }[] = [];
  horas = Array.from({ length: 13 }, (_, i) => 8 + i); 

  constructor(
    private controller: GestionarDisponibilidadController,
    private route: ActivatedRoute,
    private router: Router,
    private medicoService: MedicoService,
    private ns: NotificationService
  ) {}

  ngOnInit(): void {
    const id = +this.route.snapshot.paramMap.get('id')!;
    this.medicoService.obtenerPorId(id).subscribe(
      m => {
        this.medico = m;
        this.generarDiasConFechas();
        this.cargarDisponibilidades();
      },
      err => console.error(err)
    );
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

  private fechaToISO(fecha: string): string {
    const [dia, mes, anio] = fecha.split('/').map(p => parseInt(p, 10));
    const fullAnio = anio < 100 ? 2000 + anio : anio;
    const mesStr = mes.toString().padStart(2, '0');
    const diaStr = dia.toString().padStart(2, '0');
    return `${fullAnio}-${mesStr}-${diaStr}`;

  }

  get nombreMedico(): string {
    return this.medico.usuario ? `${this.medico.usuario.nombre} ${this.medico.usuario.apellido}` : '';
  }

  cargarDisponibilidades(): void {
    if (!this.dias.length) return;
    const fechaInicio = this.fechaToISO(this.dias[0].fecha);
    const fechaFin = this.fechaToISO(this.dias[this.dias.length - 1].fecha);

    this.controller.listarPorRango(this.medico.id, fechaInicio, fechaFin)
      .subscribe(res => this.disponibilidades = res);
  }

  toggleHorario(fecha: string, hora: number, checked: boolean) {
    if (!this.esEditable(fecha)) return;

    let dispo = this.disponibilidades.find(d =>
      d.fecha === this.fechaToISO(fecha) &&
      parseInt(d.horaInicio.split(':')[0]) === hora
    );



    if (!dispo) {
      dispo = new Disponibilidad({
        medico: this.medico,
        fecha: this.fechaToISO(fecha),
        horaInicio: `${hora.toString().padStart(2, '0')}:00:00`,
        horaFin: `${(hora + 1).toString().padStart(2, '0')}:00:00`,
        estado: checked ? EstadoDisponibilidad.DISPONIBLE : EstadoDisponibilidad.NO_DISPONIBLE
      });
      this.disponibilidades.push(dispo);
    } else {
      dispo.estado = checked ? EstadoDisponibilidad.DISPONIBLE : EstadoDisponibilidad.NO_DISPONIBLE;
    }
  }

  validarHorario(): void {
    const horasSemana = this.horasSemanaActual;
    const porDia: { [fecha: string]: number } = {};

    this.resultadoValidacion = { 
      totalValido: true, 
      distribucionValida: true, 
      diasBajos: [] 
    };

    if (horasSemana.length < this.minimoHorasSemana) {
      this.resultadoValidacion.totalValido = false;
    }

    horasSemana.forEach(d => {
      porDia[d.fecha] = (porDia[d.fecha] || 0) + 1;
    });

    const diasVisibles = this.dias.map(d => ({ 
      iso: this.fechaToISO(d.fecha), 
      nombre: d.nombre 
    }));

    diasVisibles.forEach(dia => {
      const cant = porDia[dia.iso] || 0;
      if (cant < this.minimoHorasDia) {
        this.resultadoValidacion.distribucionValida = false;
        this.resultadoValidacion.diasBajos.push(dia.nombre);
      }
    });

    if (Object.keys(porDia).length < 6) {
      this.resultadoValidacion.distribucionValida = false;
    }
  }

  guardar(): void {
    this.validarHorario();

    if (!this.resultadoValidacion.totalValido) {
      this.mensajeBloqueo = `Bloqueo Normativo: El sistema requiere un mínimo de ${this.minimoHorasSemana}h semanales. Actualmente tienes ${this.totalHorasSeleccionadas}h.`;
      this.mostrarModalBloqueo = true;
      return;
    }

    if (!this.resultadoValidacion.distribucionValida) {
      this.mostrarModalConfirmacion = true;
      return;
    }

    this.confirmarGuardado();
  }

  confirmarGuardado(): void {
    const disponibilidadesParaEnviar = this.disponibilidades.map(d => ({
      id: d.id || null,
      medico: { id: this.medico.id },
      fecha: d.fecha,
      horaInicio: d.horaInicio,
      horaFin: d.horaFin,
      estado: d.estado
    }));

    this.controller.guardarDisponibilidades(disponibilidadesParaEnviar as any)
      .subscribe({
        next: res => {
          this.disponibilidades = res.map(d => new Disponibilidad(d));
          this.mostrarModalConfirmacion = false;
          this.ns.success('Agenda guardada con éxito');

          const usuarioStr = localStorage.getItem('usuario');
          const usuarioActual = usuarioStr ? JSON.parse(usuarioStr) : null;
          const rol = usuarioActual?.rol?.toUpperCase();

          if (rol === 'ADMIN') {
            this.router.navigate(['/medicos']);
          } else {
            const usuarioId = this.medico.usuario.id;
            this.router.navigate(['/mis-citas', usuarioId]);
          }
        },
        error: err => {
          console.error(err);
          this.mostrarModalConfirmacion = false;
          this.ns.error('Error al guardar la disponibilidad');
        }
      });
  }

  get horasSemanaActual(): Disponibilidad[] {
    if (!this.dias.length) return [];

    const fechaInicioISO = this.fechaToISO(this.dias[0].fecha);
    const fechaFinISO = this.fechaToISO(this.dias[this.dias.length - 1].fecha);

    return this.disponibilidades.filter(d => 
      d.fecha >= fechaInicioISO && 
      d.fecha <= fechaFinISO && 
      d.estado === EstadoDisponibilidad.DISPONIBLE
    );
  }

  get totalHorasSeleccionadas(): number {
    return this.horasSemanaActual.length;
  }

  validarMinimoHoras(): boolean {
    const horasSemana = this.horasSemanaActual;

    if (horasSemana.length < this.minimoHorasSemana) return false;

    const porDia: { [fecha: string]: number } = {};
    horasSemana.forEach(d => {
      porDia[d.fecha] = (porDia[d.fecha] || 0) + 1;
    });

    const diasTrabajados = Object.keys(porDia);
    if (diasTrabajados.length < 6) return false;

    return diasTrabajados.every(fecha => porDia[fecha] >= this.minimoHorasDia);
  }

  esEditable(fecha: string): boolean {

    const isoFecha = this.fechaToISO(fecha);

    const lunesBase = this.getLunesDeSemana();
    const lunesBaseISO = lunesBase.toISOString().split('T')[0];

    const inicioVisible = this.fechaToISO(this.dias[0].fecha);
    const finVisible = this.fechaToISO(this.dias[this.dias.length - 1].fecha);

    return isoFecha >= inicioVisible && isoFecha <= finVisible;
  }

  siguienteSemana(): void {
    if (this.semanaIndice < 2) {
      this.semanaIndice++;
      this.generarDiasConFechas();
      this.cargarDisponibilidades();
    }
  }

  anteriorSemana(): void {
    if (this.semanaIndice > 0) {
      this.semanaIndice--;
      this.generarDiasConFechas();
      this.cargarDisponibilidades();
    }
  }

  horasFaltantes(): number {
    const faltantes = this.minimoHorasSemana - this.totalHorasSeleccionadas;
    return faltantes > 0 ? faltantes : 0;
  }

  isDisponible(fecha: string, hora: number): boolean {
    const isoFecha = this.fechaToISO(fecha);
    return this.disponibilidades.some(d =>
      d.fecha === isoFecha &&
      parseInt(d.horaInicio.split(':')[0]) === hora &&
      d.estado === EstadoDisponibilidad.DISPONIBLE
    );
  }

  getLunesDeSemana(): Date {
    const today = new Date();
    const day = today.getDay() || 7;
    const diff = today.getDate() - day + 1;
    return new Date(today.setDate(diff));
  }
}
