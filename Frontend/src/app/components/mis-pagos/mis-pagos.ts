import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { PagoService } from '../../services/pago.service';
import { Pago } from '../../models/pago.model';
import { RouterModule } from '@angular/router';
import { NotificationService } from '../../services/notification.service';
import { Subscription, interval, defer, finalize } from 'rxjs';

@Component({
  selector: 'app-mis-pagos',
  standalone: true,
  imports: [CommonModule, RouterModule],
  providers: [DatePipe],
  templateUrl: './mis-pagos.html',
  styleUrls: ['./mis-pagos.css']
})
export class MisPagosComponent implements OnInit, OnDestroy {
  pagos: Pago[] = [];
  cargando = true;
  usuario: any = null;
  today: Date = new Date();
  error = '';

  totalIngresos = 0;
  totalTransacciones = 0;
  pagosCompletados = 0;
  pagosPendientes = 0;

  mostrandoConfirmarAnular = false;
  pagoAEliminarId: number | null = null;
  private pollingSub?: Subscription;
  private cargandoPagos = false;

  constructor(
    private pagoService: PagoService,
    private ns: NotificationService
  ) {}

  ngOnInit() {
    const usrString = localStorage.getItem('usuario');
    if (usrString) {
      this.usuario = JSON.parse(usrString);
      this.cargarPagos(true);

      this.pollingSub = interval(5000).subscribe(() => {
        if (!document.hidden) {
          this.cargarPagos(false);
        }
      });
    } else {
      this.error = 'No se encontró la sesión activa.';
      this.cargando = false;
    }
  }

  ngOnDestroy() {
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
    }
  }

  cargarPagos(showLoading: boolean = true) {
    if (this.cargandoPagos || !this.usuario) return;
    this.cargandoPagos = true;
    if (showLoading) this.cargando = true;
    const request$ = defer(() => {
      const rol = this.usuario.rol?.toUpperCase();
      if (rol === 'PACIENTE') {
        return this.pagoService.obtenerPagosPorUsuario(this.usuario.id);
      }
      if (rol === 'MEDICO') {
        const medicoId = this.usuario.medicoId || this.usuario.id;
        return this.pagoService.obtenerPagosPorMedico(medicoId);
      }
      return this.pagoService.obtenerTodosLosPagos();
    });
    request$.pipe(finalize(() => { this.cargandoPagos = false; this.cargando = false; })).subscribe({
      next: (data) => {
        this.error = '';
        this.pagos = data.sort((a,b) => {
          const dateA = a.fechaPago ? new Date(a.fechaPago).getTime() : 0;
          const dateB = b.fechaPago ? new Date(b.fechaPago).getTime() : 0;
          return dateB - dateA;
        });
        this.calcularAnaliticas();
      },
      error: (err) => {
        console.error('Error al cargar pagos:', err);
        this.error = 'Error al recuperar el historial de transacciones.';
      }
    });
  }

  calcularAnaliticas() {
    this.totalTransacciones = this.pagos.length;
    this.totalIngresos = 0;
    this.pagosCompletados = 0;
    this.pagosPendientes = 0;

    this.pagos.forEach(p => {
      if (p.estadoPago === 'COMPLETADO') {
        this.totalIngresos += p.monto;
        this.pagosCompletados++;
      } else if (p.estadoPago === 'PENDIENTE') {
        this.pagosPendientes++;
      }
    });
  }

  anularPago(pagoId: number) {
    this.pagoAEliminarId = pagoId;
    this.mostrandoConfirmarAnular = true;
  }

  confirmarAnularPagoFinal() {
    if (!this.pagoAEliminarId) return;

    this.pagoService.anularPago(this.pagoAEliminarId).subscribe({
      next: () => {
        this.ns.success('Transacción anulada correctamente');
        this.cerrarModalConfirmar();
        this.cargarPagos(true);
      },
      error: (err) => {
        console.error(err);
        this.ns.error('No se pudo anular la transacción');
        this.cerrarModalConfirmar();
      }
    });
  }

  confirmarCobroFisico(pagoId: number) {
    this.cargando = true;
    this.pagoService.completarPago(pagoId).subscribe({
      next: () => {
        this.ns.success('Efectivo recibido. Pago Completado.');
        this.cargarPagos(true);
      },
      error: (err) => {
        console.error(err);
        this.ns.error('Error al registrar el cobro');
        this.cargando = false;
      }
    });
  }

  cerrarModalConfirmar() {
    this.mostrandoConfirmarAnular = false;
    this.pagoAEliminarId = null;
  }

  verComprobante(url: string) {
    window.open(url, '_blank', 'noopener,noreferrer');
  }
}
