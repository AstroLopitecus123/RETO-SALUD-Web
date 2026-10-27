import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PagoService } from '../../services/pago.service';
import { CitaService } from '../../services/cita.service';
import { CommonModule } from '@angular/common';
import { Cita } from '../../models/cita.model';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-pagar-efectivo',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pagar-efectivo.html',
  styleUrls: ['./pagar-efectivo.css']
})
export class PagarEfectivoComponent implements OnInit {

  citaId!: number;
  cita: Cita | null = null;
  cargando = true;
  procesandoPago = false;
  mensaje = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private pagoService: PagoService,
    private citaService: CitaService,
    private ns: NotificationService
  ) {}

  ngOnInit(): void {
    this.citaId = Number(this.route.snapshot.paramMap.get('id'));
    if (!this.citaId) { this.mensaje = 'ID de cita inválido'; this.cargando = false; return; }

    this.citaService.obtenerPorId(this.citaId).subscribe({
      next: (cita) => {
        this.cita = cita;
        this.cargando = false;
      },
      error: () => {
        this.mensaje = 'No se pudo cargar la información de la cita.';
        this.cargando = false;
      }
    });
  }

  confirmarPago() {
    this.procesandoPago = true;
    const usuarioRaw = localStorage.getItem('usuario');
    if (!usuarioRaw) { this.router.navigate(['/']); return; }

    const usuario = JSON.parse(usuarioRaw);
    const body = {
      citaId: this.citaId,
      usuarioId: usuario.id,
      monto: (this.cita as any)?.medico?.especialidad?.precioBase ?? 20.00
    };

    setTimeout(() => {
      this.pagoService.prometerEfectivo(body).subscribe({
        next: () => {
          this.ns.success('Voucher generado y Cita Confirmada correctamente');
          this.procesandoPago = false;
          setTimeout(() => this.router.navigate(['/paciente/dashboard']), 1500);
        },
        error: (err) => {
          console.error(err);
          this.ns.error('Error al procesar el pago. Intente nuevamente.');
          this.procesandoPago = false;
        }
      });
    }, 2000);
  }

  volver() {
    this.router.navigate(['/mis-citas']);
  }
}
