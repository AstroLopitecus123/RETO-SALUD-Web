import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin, defer } from 'rxjs';
import { CitaService } from '../../services/cita.service';
import { MedicoService } from '../../services/medico.service';

import { NotificacionesComponent } from '../notificaciones/notificaciones';

@Component({
  selector: 'app-recepcion-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, NotificacionesComponent],
  templateUrl: './recepcion-dashboard.html',
  styleUrls: ['./recepcion-dashboard.css']
})
export class RecepcionDashboardComponent implements OnInit {
  usuario: any = null;
  cargando = true;
  error = '';
  today = new Date();
  stats = {
    citasHoy: 0,
    medicosRegistrados: 0,
    pendientesConfirmar: 0
  };

  constructor(private citaService: CitaService, private medicoService: MedicoService) {}

  ngOnInit() {
    const usuarioStr = localStorage.getItem('usuario');
    if (usuarioStr) {
      this.usuario = JSON.parse(usuarioStr);
    }
    this.cargarDatos();
  }

  cargarDatos() {

    this.cargando = true;
    this.error = '';
    forkJoin({
      citas: defer(() => this.citaService.listarTodas()),
      medicos: defer(() => this.medicoService.listarMedicos())
    }).subscribe({
      next: ({ citas, medicos }) => {
        this.today = new Date();
        this.stats = {
          citasHoy: citas.filter(c => new Date(c.fecha).toDateString() === this.today.toDateString()).length,
          pendientesConfirmar: citas.filter(c => c.estado === 'PENDIENTE').length,
          medicosRegistrados: medicos.length
        };
        this.cargando = false;
      },
      error: () => {
        this.error = 'No se pudieron cargar las métricas de recepción.';
        this.cargando = false;
      }
    });
  }

  getGreeting(): string {
    const hours = new Date().getHours();
    if (hours < 12) return 'Buenos días';
    return 'Buenas tardes';
  }
}
