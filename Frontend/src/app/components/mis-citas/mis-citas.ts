import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { CitaService } from '../../services/cita.service';
import { MisCitasController } from '../../controller/mis-citas.controller';
import { FormsModule } from '@angular/forms';
import { UsuarioService } from '../../services/usuario.service';
import { ActivatedRoute } from '@angular/router'; 
import { Router} from '@angular/router';
import { MedicoService} from '../../services/medico.service';
import { HistorialService } from '../../services/historial.service';
import { NotificationService } from '../../services/notification.service';
import { Subscription, interval } from 'rxjs';

@Component({
  selector: 'app-mis-citas',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  providers: [DatePipe],
  templateUrl: './mis-citas.html',
  styleUrls: ['./mis-citas.css']
})
export class MisCitasComponent implements OnInit, OnDestroy {
  ctrl: MisCitasController;
  private pollingSub?: Subscription;

  constructor(
    private citaService: CitaService,
    private usuarioService: UsuarioService,
    private route: ActivatedRoute,
    private router: Router,
    private medicoService: MedicoService,
    private historialService: HistorialService,
    private ns: NotificationService
  ) {

    this.ctrl = new MisCitasController(
      this.citaService, 
      this.usuarioService, 
      this.route, 
      this.router, 
      this.medicoService, 
      this.historialService,
      this.ns
    );
  }

  ngOnInit(): void {
    this.ctrl.inicializar(); 

    this.pollingSub = interval(5000).subscribe(() => {

      if (!document.hidden && !this.ctrl.cargando && !this.ctrl.editando && !this.ctrl.mostrandoModalCita && !this.ctrl.mostrandoModalCancelar && !this.ctrl.mostrandoModalHistorial) {
         this.ctrl.cargarCitas(false);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.pollingSub) {
      this.pollingSub.unsubscribe();
    }
  }
}
