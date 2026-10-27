import { Component, OnInit, HostListener, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { AppComponent } from '../../app';
import { VoiceAccessibilityService } from '../../services/voice-accessibility.service';
import { NarratorService } from '../../services/narrator.service';
import { NotificationService } from '../../services/notification.service';

export type TipoFiltro =
  | 'NINGUNO'
  | 'DEUTERANOPIA'
  | 'PROTANOPIA'
  | 'TRITANOPIA'
  | 'DEUTERANOMALIA'
  | 'PROTANOMALIA'
  | 'ACROMATOPSIA';

const FILTROS: Record<TipoFiltro, string> = {
  NINGUNO: 'none',
  DEUTERANOPIA: 'url(#deuteranopia)',
  PROTANOPIA:   'url(#protanopia)',
  TRITANOPIA:   'url(#tritanopia)',
  DEUTERANOMALIA: 'url(#deuteranomalia)',
  PROTANOMALIA:   'url(#protanomalia)',
  ACROMATOPSIA:   'grayscale(100%)',
};

const LS_KEY = 'accesibilidad-filtro';

@Component({
  selector: 'app-accesibilidad',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './accesibilidad.html',
  styleUrls: ['./accesibilidad.css']
})
export class AccesibilidadComponent implements OnInit {
  abierto = false;
  filtroActivo: TipoFiltro = 'NINGUNO';

  @Output() confirmadoWizard = new EventEmitter<TipoFiltro>();
  esModoWizard = false;

  constructor(
    private http: HttpClient,
    private app: AppComponent,
    public voiceService: VoiceAccessibilityService,
    private narratorService: NarratorService,
    private notificationService: NotificationService
  ) {}

  modoPreview = false;
  filtroPreview: TipoFiltro = 'NINGUNO';
  cuentaRegresiva = 10;
  private timerPreview: any;

  vistaActual: 'PRINCIPAL' | 'VISION' | 'ZOOM' | 'CONTRASTE' | 'AUDIO' | 'NAVEGACION' = 'PRINCIPAL';

  nivelZoom = 1; 
  contrasteActivo = false;

  customBg = '#ffffff';
  customText = '#333333';
  esPersonalizado = false;

  vozActiva = false;
  narradorActivo = false;
  narradorPaginaActivo = false;
  modoTeclado = false;
  sonidoNotificaciones = true;

  mostrandoTest = false;
  pasoTest = 0; 
  diagnostico: TipoFiltro[] = [];
  resultadoTest: { tipo: TipoFiltro; etiqueta: string; descripcion: string } | null = null;

  opciones = [
    { 
      tipo: 'DEUTERANOPIA' as TipoFiltro,   
      etiqueta: 'Deuteranopia',    
      icono: '🟢', 
      color: '#16a34a',
      desc: 'Dificultad progresiva para distinguir el color verde.'
    },
    { 
      tipo: 'PROTANOPIA' as TipoFiltro,     
      etiqueta: 'Protanopia',      
      icono: '', 
      color: '#dc2626',
      desc: 'Dificultad para percibir la luz roja.'
    },
    { 
      tipo: 'TRITANOPIA' as TipoFiltro,     
      etiqueta: 'Tritanopia',      
      icono: '🔵', 
      color: '#2563eb',
      desc: 'Dificultad rara para distinguir los colores azules y amarillos.'
    },
    { tipo: 'DEUTERANOMALIA' as TipoFiltro, etiqueta: 'Deuteranomalía',  icono: '🟡', color: '#ca8a04', desc: 'Versión leve de la deuteranopia.' },
    { tipo: 'PROTANOMALIA' as TipoFiltro,   etiqueta: 'Protanomalía',    icono: '🟠', color: '#ea580c', desc: 'Versión leve de la protanopia.' },
    { tipo: 'ACROMATOPSIA' as TipoFiltro,   etiqueta: 'Escala Grises',   icono: '⚫', color: '#475569', desc: 'Visión en blanco y negro.' },
    { tipo: 'NINGUNO' as TipoFiltro,        etiqueta: 'Sin filtro',      icono: '✖',  color: '#94a3b8', desc: 'Visión estándar.' },
  ];

  ngOnInit() {
    const fActivo = localStorage.getItem(LS_KEY) as TipoFiltro | null;
    const zNivel = localStorage.getItem('DOCGOSI_ZOOM');
    const cActivo = localStorage.getItem('DOCGOSI_CONTRASTE');
    const customActivo = localStorage.getItem('DOCGOSI_CUSTOM_ACTIVE');
    const cBg = localStorage.getItem('DOCGOSI_CUSTOM_BG');
    const cText = localStorage.getItem('DOCGOSI_CUSTOM_TEXT');

    const vActiva = localStorage.getItem('DOCGOSI_VOICE_ACTIVE') === 'true';
    const nActivo = localStorage.getItem('DOCGOSI_NARRATOR_ACTIVE') === 'true';
    const npActivo = localStorage.getItem('DOCGOSI_PAGE_NARRATOR_ACTIVE') === 'true';
    const tModo = localStorage.getItem('DOCGOSI_KEYBOARD_MODE') === 'true';
    const sNotif = localStorage.getItem('DOCGOSI_NOTIF_SOUNDS');

    if (sNotif === 'false') {
      this.sonidoNotificaciones = false;
    }

    if (fActivo && fActivo !== 'NINGUNO') {
      this.filtroActivo = fActivo;
      this.aplicarFiltro(fActivo);
    }

    if (zNivel) {
      this.nivelZoom = parseFloat(zNivel);
      this.aplicarZoom(this.nivelZoom);
    }

    if (cActivo === 'true') {
      this.contrasteActivo = true;
      this.aplicarContraste(true);
    }

    if (cBg) this.customBg = cBg;
    if (cText) this.customText = cText;
    if (customActivo === 'true') {
      this.esPersonalizado = true;
      this.aplicarColoresPersonalizados(true);
    }

    if (vActiva) this.toggleVoz(true);
    if (nActivo) this.toggleNarrador(true);
    if (npActivo) this.toggleNarradorPagina(true);
    if (tModo) this.toggleTeclado(true);

    this.voiceService.commandDetected.subscribe(msg => {
      this.notificationService.info(msg);
    });
  }

  toggleMenu() { 
    this.abierto = !this.abierto; 
    if (this.abierto) {
      this.mostrandoTest = false;
      this.vistaActual = 'PRINCIPAL';
      setTimeout(() => {
        if (this.abierto) document.querySelector<HTMLElement>('#accessibility-panel button')?.focus();
      });
    }
  }

  cambiarVista(nueva: 'PRINCIPAL' | 'VISION' | 'ZOOM' | 'CONTRASTE' | 'AUDIO' | 'NAVEGACION') {
    this.vistaActual = nueva;
  }

  ajustarZoom(delta: number) {
    this.nivelZoom = Math.min(Math.max(this.nivelZoom + delta, 0.8), 1.5);
    this.aplicarZoom(this.nivelZoom);
    localStorage.setItem('DOCGOSI_ZOOM', this.nivelZoom.toString());
  }

  toggleContraste() {
    this.contrasteActivo = !this.contrasteActivo;

    if (!this.contrasteActivo) {
      this.esPersonalizado = false;
      this.aplicarColoresPersonalizados(false);
      this.aplicarContraste(false);
    } else {

      if (!this.esPersonalizado) {
        this.aplicarContraste(true);
      }
    }

    localStorage.setItem('DOCGOSI_CONTRASTE', this.contrasteActivo.toString());
    localStorage.setItem('DOCGOSI_CUSTOM_ACTIVE', this.esPersonalizado.toString());
  }

  actualizarColor(tipo: 'BG' | 'TEXT', event: Event) {
    const val = (event.target as HTMLInputElement).value;
    if (tipo === 'BG') this.customBg = val;
    else this.customText = val;

    this.esPersonalizado = true;
    this.contrasteActivo = true; 
    this.aplicarContraste(false); 
    this.aplicarColoresPersonalizados(true);

    localStorage.setItem('DOCGOSI_CUSTOM_BG', this.customBg);
    localStorage.setItem('DOCGOSI_CUSTOM_TEXT', this.customText);
    localStorage.setItem('DOCGOSI_CUSTOM_ACTIVE', 'true');
    localStorage.setItem('DOCGOSI_CONTRASTE', 'true');
  }

  resetearColores() {
    this.customBg = '#ffffff';
    this.customText = '#333333';
    this.esPersonalizado = false;
    this.contrasteActivo = false;
    this.aplicarColoresPersonalizados(false);
    localStorage.removeItem('DOCGOSI_CUSTOM_BG');
    localStorage.removeItem('DOCGOSI_CUSTOM_TEXT');
    localStorage.setItem('DOCGOSI_CUSTOM_ACTIVE', 'false');
    localStorage.setItem('DOCGOSI_CONTRASTE', 'false');
  }

  toggleVoz(forzar?: boolean) {
    this.vozActiva = forzar !== undefined ? forzar : !this.vozActiva;
    if (this.vozActiva) {
      this.voiceService.startListening();
    } else {
      this.voiceService.stopListening();
    }
    localStorage.setItem('DOCGOSI_VOICE_ACTIVE', this.vozActiva.toString());
  }

  toggleNarrador(forzar?: boolean) {
    this.narradorActivo = forzar !== undefined ? forzar : !this.narradorActivo;
    if (!this.narradorActivo) this.narratorService.stop();
    localStorage.setItem('DOCGOSI_NARRATOR_ACTIVE', this.narradorActivo.toString());
  }

  toggleNarradorPagina(forzar?: boolean) {
    this.narradorPaginaActivo = forzar !== undefined ? forzar : !this.narradorPaginaActivo;
    if (this.narradorPaginaActivo) {
      this.narratorService.activateGlobalNarrator();
    } else {
      this.narratorService.deactivateGlobalNarrator();
    }
    localStorage.setItem('DOCGOSI_PAGE_NARRATOR_ACTIVE', this.narradorPaginaActivo.toString());
  }

  toggleTeclado(forzar?: boolean) {
    this.modoTeclado = forzar !== undefined ? forzar : !this.modoTeclado;
    if (this.modoTeclado) {
      document.body.classList.add('focus-pro');
    } else {
      document.body.classList.remove('focus-pro');
    }
    localStorage.setItem('DOCGOSI_KEYBOARD_MODE', this.modoTeclado.toString());
  }

  toggleSonidoNotificaciones(forzar?: boolean) {
    this.sonidoNotificaciones = forzar !== undefined ? forzar : !this.sonidoNotificaciones;
    localStorage.setItem('DOCGOSI_NOTIF_SOUNDS', this.sonidoNotificaciones.toString());
  }

  private aplicarColoresPersonalizados(activo: boolean) {
    if (activo) {
      document.documentElement.style.setProperty('--custom-bg', this.customBg);
      document.documentElement.style.setProperty('--custom-text', this.customText);
      document.body.classList.add('custom-colors');
    } else {
      document.body.classList.remove('custom-colors');
    }
  }

  private aplicarZoom(nivel: number) {
    document.documentElement.style.setProperty('--app-zoom', nivel.toString());
    document.documentElement.style.fontSize = `${nivel * 100}%`;
  }

  private aplicarContraste(activo: boolean) {
    if (activo) {
      document.body.classList.add('high-contrast');
    } else {
      document.body.classList.remove('high-contrast');
    }
  }

  iniciarTest() {
    this.abierto = false;
    this.mostrandoTest = true;
    this.pasoTest = 0;
    this.diagnostico = [];
    this.resultadoTest = null;
  }

  respuestaTest(veBien: boolean, tipoSugerido: TipoFiltro) {
    if (!veBien) {
      this.diagnostico.push(tipoSugerido);
    }
    this.pasoTest++;
    if (this.pasoTest > 5) {
      this.finalizarTest();
    }
  }

  finalizarTest() {
    let tipoDetectado: TipoFiltro = 'NINGUNO';
    if (this.diagnostico.length > 0) {
      const counts: { [key: string]: number } = {};
      this.diagnostico.forEach(t => counts[t] = (counts[t] || 0) + 1);
      tipoDetectado = Object.keys(counts).reduce((a, b) => counts[a] > counts[b] ? a : b) as TipoFiltro;
    }
    const info = this.opciones.find(o => o.tipo === tipoDetectado);
    this.resultadoTest = {
      tipo: tipoDetectado,
      etiqueta: info?.etiqueta || 'Normal',
      descripcion: info?.desc || 'No se han detectado anomalías significativas.'
    };
    this.pasoTest = 6;
  }

  probarResultado() {
    if (this.resultadoTest) {
      this.mostrandoTest = false;
      this.iniciarPreview(this.resultadoTest.tipo);
    }
  }

  seleccionar(tipo: TipoFiltro) {
    this.abierto = false;
    if (tipo === 'NINGUNO') {
      this.desactivar();
      return;
    }
    this.iniciarPreview(tipo);
  }

  iniciarPreview(tipo: TipoFiltro) {
    this.filtroPreview = tipo;
    this.modoPreview = true;
    this.cuentaRegresiva = 10;
    this.aplicarFiltro(tipo);
    this.timerPreview = setInterval(() => {
      this.cuentaRegresiva--;
      if (this.cuentaRegresiva <= 0) this.cancelarPreview();
    }, 1000);
  }

  abrirAsistente(esNuevoUsuario = false) {
    this.esModoWizard = esNuevoUsuario;
    this.iniciarTest();
  }

  confirmarPreview() {
    clearInterval(this.timerPreview);
    this.filtroActivo = this.filtroPreview;
    localStorage.setItem(LS_KEY, this.filtroActivo);
    this.modoPreview = false;
    if (this.esModoWizard) {
      this.confirmadoWizard.emit(this.filtroActivo);
      this.esModoWizard = false;
    }
    if (this.app.isLoggedIn && this.app.usuario?.id) {
      this.http.put(`/api/usuarios/${this.app.usuario.id}/configuracion-visual`, {
        configuracionVisual: this.filtroActivo
      }).subscribe({
        next: () => console.log(' Configuración visual guardada'),
        error: (err: any) => console.error('❌ Error:', err)
      });
    }
  }

  cancelarPreview() {
    clearInterval(this.timerPreview);
    this.modoPreview = false;
    this.aplicarFiltro(this.filtroActivo);
  }

  desactivar() {
    this.filtroActivo = 'NINGUNO';
    localStorage.setItem(LS_KEY, 'NINGUNO');
    this.aplicarFiltro('NINGUNO');
  }

  private aplicarFiltro(tipo: TipoFiltro) {
    document.documentElement.style.filter = FILTROS[tipo];
  }

  etiquetaActiva(): string {
    return this.opciones.find(o => o.tipo === this.filtroActivo)?.etiqueta ?? 'Sin filtro';
  }

  etiquetaPreview(): string {
    return this.opciones.find(o => o.tipo === this.filtroPreview)?.etiqueta ?? 'Vista previa';
  }

  @HostListener('document:click', ['$event'])
  cerrarAlClickFuera(e: MouseEvent) {
    const el = (e.target as HTMLElement).closest('app-accesibilidad');
    if (!el && this.abierto) this.abierto = false;
  }

  @HostListener('document:keydown', ['$event'])
  manejarTecladoGlobal(e: KeyboardEvent) {
    if (e.key === 'Escape' && this.abierto) {
      this.abierto = false;
      document.querySelector<HTMLElement>('app-accesibilidad .acc-fab')?.focus();
      return;
    }
    const target = e.target as HTMLElement;
    if (!this.modoTeclado || e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey || e.shiftKey ||
        target.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"]), [role="textbox"], [role="slider"], [role="combobox"], [role="listbox"], [role="menu"]') ||
        document.querySelector('[aria-modal="true"]:not([hidden])')) return;

    const teclasNavegacion = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];
    if (teclasNavegacion.includes(e.key)) {
      e.preventDefault();
      this.moverFoco(e.key as 'ArrowUp' | 'ArrowDown' | 'ArrowLeft' | 'ArrowRight');
    }
  }

  private moverFoco(tecla: 'ArrowUp' | 'ArrowDown' | 'ArrowLeft' | 'ArrowRight') {
    const selector = 'button, a, input, select, textarea, [tabindex="0"]';
    const candidatos = Array.from(document.querySelectorAll(selector))
      .filter(el => {
        const rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0 && (el as HTMLElement).tabIndex >= 0 &&
          !el.matches(':disabled') && !el.closest('[inert], [hidden], [aria-hidden="true"]');
      }) as HTMLElement[];

    const actual = document.activeElement as HTMLElement;
    if (!actual || actual === document.body) {
      candidatos[0]?.focus();
      return;
    }

    const rectActual = actual.getBoundingClientRect();
    const centroActual = {
      x: rectActual.left + rectActual.width / 2,
      y: rectActual.top + rectActual.height / 2
    };

    let mejorCandidato: HTMLElement | null = null;
    let minimaDistancia = Infinity;

    for (const cand of candidatos) {
      if (cand === actual) continue;

      const rectCand = cand.getBoundingClientRect();
      const centroCand = {
        x: rectCand.left + rectCand.width / 2,
        y: rectCand.top + rectCand.height / 2
      };

      const dx = centroCand.x - centroActual.x;
      const dy = centroCand.y - centroActual.y;

      let esValido = false;
      const margen = 5; 

      switch (tecla) {
        case 'ArrowUp':    if (dy < -margen) esValido = true; break;
        case 'ArrowDown':  if (dy > margen)  esValido = true; break;
        case 'ArrowLeft':  if (dx < -margen) esValido = true; break;
        case 'ArrowRight': if (dx > margen)  esValido = true; break;
      }

      if (esValido) {
        const distancia = Math.sqrt(dx * dx + dy * dy);

        const penalizacionLateral = (tecla === 'ArrowUp' || tecla === 'ArrowDown') 
          ? Math.abs(dx) * 8 
          : Math.abs(dy) * 5;

        const distanciaTotal = distancia + penalizacionLateral;

        if (distanciaTotal < minimaDistancia) {
          minimaDistancia = distanciaTotal;
          mejorCandidato = cand;
        }
      }
    }

    if (!mejorCandidato) {

       for (const cand of candidatos) {
          if (cand === actual) continue;
          const rectCand = cand.getBoundingClientRect();
          const dy = (rectCand.top + rectCand.height/2) - centroActual.y;
          if (tecla === 'ArrowDown' && dy > 0 && rectCand.top < minimaDistancia) {
             mejorCandidato = cand;
             minimaDistancia = rectCand.top;
          }
       }
    }

    if (mejorCandidato) {
      mejorCandidato.focus();
      mejorCandidato.scrollIntoView({ behavior: 'smooth', block: 'center' });
    } else {

      this.notificarFinDireccion();
    }
  }

  private notificarFinDireccion() {

  }
}
