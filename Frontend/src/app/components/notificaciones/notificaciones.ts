import { Component, OnInit, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NotificacionService, Notificacion } from '../../services/notificacion.service';
import { NotificationService } from '../../services/notification.service';
import { catchError, defer, forkJoin, map, of } from 'rxjs';

@Component({
  selector: 'app-notificaciones',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notificaciones.html',
  styleUrls: ['./notificaciones.css']
})
export class NotificacionesComponent implements OnInit {
  notificaciones: Notificacion[] = [];
  filtro: 'TODAS' | 'NO_LEIDAS' = 'TODAS';
  loading = true;
  error = '';
  errorAccion = '';
  marcandoTodas = false;
  notificacionSeleccionada: Notificacion | null = null;
  mostrarModal = false;

  @Input() modoDashboard = false;

  constructor(
    private notificacionService: NotificacionService,
    private ns: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.cargarNotificaciones();
  }

  cargarNotificaciones(): void {
    this.loading = true;
    this.error = '';
    defer(() => this.notificacionService.getMisNotificaciones()).subscribe({
      next: (res) => {
        this.notificaciones = res.sort((a, b) => 
          new Date(b.fechaCreacion).getTime() - new Date(a.fechaCreacion).getTime()
        );
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.error = 'No se pudieron cargar las notificaciones.';
      }
    });
  }

  get notificacionesFiltradas(): Notificacion[] {
    if (this.filtro === 'NO_LEIDAS') {
      return this.notificaciones.filter(n => !n.leida);
    }
    return this.notificaciones;
  }

  getCountNoLeidas(): number {
    return this.notificaciones.filter(n => !n.leida).length;
  }

  marcarLeida(notif: Notificacion): void {
    if (!notif.leida) {
      this.notificacionService.marcarComoLeida(notif.id).subscribe({
        next: () => {
          notif.leida = true;
          this.notificacionService.notificarCambio(); 
        },
        error: () => { this.errorAccion = 'No se pudo marcar la notificación como leída.'; }
      });
    }

    this.notificacionSeleccionada = notif;
    this.mostrarModal = true;
  }

  cerrarModal(): void {
    this.mostrarModal = false;
    this.notificacionSeleccionada = null;
  }

  verCitaRelacionada(): void {
    const notif = this.notificacionSeleccionada;
    if (!notif || !notif.referenciaId) {
      this.router.navigate(['/mis-citas']);
      this.cerrarModal();
      return;
    }

    const t = notif.titulo.toLowerCase();
    let accion = '';

    if (t.includes('reprogramar') || t.includes('reprogramación')) {
      accion = 'reprogramar';
    } else if (t.includes('cancelar') || t.includes('cancelación')) {
      accion = 'cancelar';
    }

    this.router.navigate(['/mis-citas'], { 
      queryParams: { 
        idCita: notif.referenciaId, 
        accion: accion 
      } 
    });
    this.cerrarModal();
  }

  marcarTodasComoLeidas(): void {
    if (this.marcandoTodas || this.loading || this.error) return;
    const noLeidas = this.notificaciones.filter(n => !n.leida);
    if (noLeidas.length === 0) return;
    this.marcandoTodas = true;
    this.errorAccion = '';
    forkJoin(noLeidas.map(n => defer(() => this.notificacionService.marcarComoLeida(n.id)).pipe(
      map(() => ({ notificacion: n, ok: true })),
      catchError(() => of({ notificacion: n, ok: false }))
    ))).subscribe(resultados => {
      resultados.forEach(r => { if (r.ok) r.notificacion.leida = true; });
      this.marcandoTodas = false;
      if (resultados.some(r => r.ok)) this.notificacionService.notificarCambio();
      if (resultados.some(r => !r.ok)) {
        this.errorAccion = 'No se pudieron marcar todas las notificaciones. Puedes reintentar las pendientes.';
      }
    });
  }

  getIconClass(titulo: string): string {
    const t = titulo.toLowerCase();
    if (t.includes('cancel')) return 'fa-calendar-xmark';
    if (t.includes('confirm')) return 'fa-calendar-check';
    if (t.includes('pago') || t.includes('reembolso')) return 'fa-credit-card';
    if (t.includes('reprogramar')) return 'fa-calendar-days';
    return 'fa-bell';
  }

  getIconBackground(titulo: string): string {
    const t = titulo.toLowerCase();
    if (t.includes('cancel')) return 'rgba(239, 68, 68, 0.1)';
    if (t.includes('confirm')) return 'rgba(16, 185, 129, 0.1)';
    if (t.includes('pago') || t.includes('reembolso')) return 'rgba(59, 130, 246, 0.1)';
    return 'rgba(0, 0, 0, 0.05)';
  }
}
