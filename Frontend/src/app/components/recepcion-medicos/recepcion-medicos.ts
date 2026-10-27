import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { defer } from 'rxjs';
import { MedicoService } from '../../services/medico.service';
import { UsuarioFull } from '../../models/usuario-full.model';

@Component({
  selector: 'app-recepcion-medicos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="dashboard-page-content">
      <header class="premium-welcome-banner">
        <div class="welcome-text"><h1>Consulta de M&eacute;dicos</h1><p>Directorio de solo lectura.</p></div>
        <a routerLink="/recepcion/dashboard">Volver a recepci&oacute;n</a>
      </header>
      <section class="premium-card">
        <p *ngIf="cargando" role="status">Cargando m&eacute;dicos...</p>
        <div *ngIf="!cargando && error" role="alert">
          <p>{{ error }}</p><button (click)="cargarMedicos()">Reintentar</button>
        </div>
        <ng-container *ngIf="!cargando && !error">
          <p *ngIf="medicos.length === 0">No hay m&eacute;dicos registrados.</p>
          <div class="modern-table-container" *ngIf="medicos.length > 0">
            <table class="modern-table">
              <thead><tr><th>M&eacute;dico</th><th>Especialidad</th></tr></thead>
              <tbody><tr *ngFor="let medico of medicos">
                <td>{{ medico.nombre }} {{ medico.apellido }}</td>
                <td>{{ medico.especialidad?.nombre || 'Sin especialidad registrada' }}</td>
              </tr></tbody>
            </table>
          </div>
        </ng-container>
      </section>
    </div>
  `
})
export class RecepcionMedicosComponent implements OnInit {
  medicos: UsuarioFull[] = [];
  cargando = true;
  error = '';

  constructor(private medicoService: MedicoService) {}

  ngOnInit(): void { this.cargarMedicos(); }

  cargarMedicos(): void {
    this.cargando = true;
    this.error = '';
    defer(() => this.medicoService.listarMedicos()).subscribe({
      next: data => {
        this.medicos = data.map(m => new UsuarioFull(m));
        this.cargando = false;
      },
      error: () => {
        this.error = 'No se pudo cargar el directorio de medicos.';
        this.cargando = false;
      }
    });
  }
}
