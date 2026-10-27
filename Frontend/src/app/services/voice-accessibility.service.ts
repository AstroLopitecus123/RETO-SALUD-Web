import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';

interface BrowserRecognition {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onstart: (() => void) | null;
  onend: (() => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onresult: ((event: {
    resultIndex: number;
    results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }>;
  }) => void) | null;
  start(): void;
  abort(): void;
}

@Injectable({
  providedIn: 'root'
})
export class VoiceAccessibilityService implements OnDestroy {

  private recognition: BrowserRecognition | null = null;
  private restartTimer: ReturnType<typeof setTimeout> | null = null;
  public isListening: boolean = false;
  public statusMessage = 'Control por voz desactivado.';
  public errorMessage = '';

  commandDetected = new Subject<string>();

  constructor(_http: HttpClient, private router: Router) { }

  startListening() {
    if (this.isListening) return;

    this.errorMessage = '';
    const browser = typeof window === 'undefined' ? undefined : window as Window & {
      SpeechRecognition?: new () => BrowserRecognition;
      webkitSpeechRecognition?: new () => BrowserRecognition;
    };
    const Recognition = browser?.SpeechRecognition || browser?.webkitSpeechRecognition;
    if (!Recognition) {
      this.fail('Este navegador no admite reconocimiento de voz. Usa un navegador compatible con Web Speech API.');
      return;
    }

    try {
      const recognition = new Recognition();
      this.recognition = recognition;
      recognition.lang = 'es-ES';
      recognition.continuous = true;
      recognition.interimResults = false;
      this.isListening = true;
      this.statusMessage = 'Solicitando acceso al microfono...';
      let restarts = 0;
      recognition.onstart = () => {
        if (this.recognition !== recognition) return;
        this.statusMessage = 'Control por voz activo.';
        document.body.classList.add('voice-active');
        this.commandDetected.next(this.statusMessage);
      };
      recognition.onresult = event => {
        if (this.recognition !== recognition || !this.isListening) return;
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const result = event.results[i];
          if (result.isFinal && result[0].transcript.trim()) {
            restarts = 0;
            this.handleTranscripcion(result[0].transcript);
          }
        }
      };
      recognition.onerror = event => {
        if (this.recognition !== recognition || event.error === 'no-speech') return;
        const errors: Record<string, string> = {
          'not-allowed': 'Permiso de microfono denegado. Habilitalo en el navegador y activa la voz nuevamente.',
          'service-not-allowed': 'El navegador no permite el servicio de reconocimiento de voz.',
          'audio-capture': 'No se encontro un microfono disponible.',
          'network': 'El reconocimiento de voz no pudo conectarse. Revisa tu conexion.',
          'language-not-supported': 'El navegador no admite reconocimiento de voz en espanol.'
        };
        this.fail(errors[event.error] || 'El reconocimiento de voz se interrumpio. Activalo nuevamente para reintentar.');
      };
      recognition.onend = () => {
        if (this.recognition !== recognition || !this.isListening) return;
        document.body.classList.remove('voice-active');
        // Algunos navegadores terminan la escucha continua incluso sin pedirlo.
        if (++restarts > 3) {
          this.fail('El navegador detuvo la escucha. Activa el control por voz nuevamente.');
          return;
        }
        this.statusMessage = 'Reanudando control por voz...';
        this.restartTimer = setTimeout(() => {
          this.restartTimer = null;
          if (this.recognition !== recognition || !this.isListening) return;
          try {
            recognition.start();
          } catch {
            this.fail('No se pudo reanudar el control por voz. Activalo nuevamente.');
          }
        }, 300);
      };
      recognition.start();
    } catch {
      this.fail('No se pudo iniciar el reconocimiento de voz. Revisa los permisos del microfono.');
    }
  }

  stopListening() {
    this.isListening = false;
    this.statusMessage = 'Control por voz desactivado.';
    if (typeof document !== 'undefined') document.body.classList.remove('voice-active');
    if (this.restartTimer !== null) clearTimeout(this.restartTimer);
    this.restartTimer = null;
    const recognition = this.recognition;
    this.recognition = null;
    if (!recognition) return;
    recognition.onstart = recognition.onend = recognition.onerror = recognition.onresult = null;
    try { recognition.abort(); } catch { /* Ya puede estar detenido. */ }
  }

  private fail(message: string) {
    this.stopListening();
    this.errorMessage = message;
    this.statusMessage = message;
    this.commandDetected.next(message);
  }

  private handleTranscripcion(text: string) {
    // Limpiar puntuacion del reconocimiento.
    const cleanText = text.toLowerCase().replace(/[.,!¡¿?]/g, '').trim();

    // INICIO
    if (cleanText.includes('ir a inicio') || cleanText.includes('ir al inicio') || cleanText.includes('vuelve al inicio') || cleanText.includes('volver al inicio')) {
      this.router.navigate(['/']);
      this.commandDetected.next('Navegando al Inicio');
    } 
    // REGISTRO
    else if (cleanText.includes('ir a registro') || cleanText.includes('ir al registro') || cleanText.includes('crear cuenta') || cleanText.includes('registrarse')) {
      this.router.navigate(['/registrar']);
      this.commandDetected.next('Abriendo Registro');
    }
    // LOGIN
    else if (cleanText.includes('ir a login') || cleanText.includes('ir al login') || cleanText.includes('inicia sesión') || cleanText.includes('iniciar sesión') || cleanText.includes('inicia la sesión')) {
      this.router.navigate(['/login']);
      this.commandDetected.next('Yendo a Login');
    }
    // MENÚS LATERALES (Hacer clic en los enlaces por su texto)
    else if (cleanText.startsWith('menú ') || cleanText.startsWith('ir al menú ') || cleanText.startsWith('ir a menú ')) {
      const menuText = cleanText.replace('ir al menú ', '').replace('ir a menú ', '').replace('menú ', '').trim();
      const menuLink = Array.from(document.querySelectorAll('a, .nav-item')).find(el => el.textContent?.toLowerCase().includes(menuText)) as HTMLElement;
      if (menuLink) {
        menuLink.click();
        this.commandDetected.next(`Abriendo menú: ${menuText}`);
      }
    }
    // AGENDAR CITA
    else if (cleanText.includes('nueva cita')) {
      const btnNuevaCita = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.toLowerCase().includes('nueva cita'));
      if (btnNuevaCita) { btnNuevaCita.click(); this.commandDetected.next('Abriendo modal Nueva Cita'); }
    }
    // DROPDOWNS ESPECIALIDAD / ESPECIALISTA
    else if (cleanText.startsWith('seleccionar especialidad')) {
      const especialidad = cleanText.replace('seleccionar especialidad', '').trim();
      const selects = document.querySelectorAll('select');
      if (selects.length > 0 && especialidad) {
        const select = selects[0] as HTMLSelectElement;
        const optionToSelect = Array.from(select.options).find(opt => opt.text.toLowerCase().includes(especialidad));
        if (optionToSelect) {
          select.value = optionToSelect.value;
          select.dispatchEvent(new Event('change', { bubbles: true }));
          this.commandDetected.next(`Especialidad seleccionada: ${optionToSelect.text}`);
        }
      } else if (selects.length > 0) {
        (selects[0] as HTMLSelectElement).focus();
        this.commandDetected.next('Seleccione especialidad');
      }
    }
    else if (cleanText.startsWith('seleccionar especialista') || cleanText.startsWith('seleccionar doctor')) {
      const especialista = cleanText.replace('seleccionar especialista', '').replace('seleccionar doctor', '').trim();
      const selects = document.querySelectorAll('select');
      if (selects.length > 1 && especialista) {
        const select = selects[1] as HTMLSelectElement;
        const optionToSelect = Array.from(select.options).find(opt => opt.text.toLowerCase().includes(especialista));
        if (optionToSelect) {
          select.value = optionToSelect.value;
          select.dispatchEvent(new Event('change', { bubbles: true }));
          this.commandDetected.next(`Médico seleccionado: ${optionToSelect.text}`);
        }
      } else if (selects.length > 1) {
        (selects[1] as HTMLSelectElement).focus();
        this.commandDetected.next('Seleccione especialista');
      }
    }
    // CALENDARIO
    else if (cleanText.startsWith('seleccionar ') && cleanText.includes(' a las ')) {
      const match = cleanText.match(/seleccionar (.*?) a las (\d{1,2})/);
      if (match) {
        const dia = match[1].trim();
        let hora = match[2].trim();
        const table = document.querySelector('.schedule-table') as HTMLTableElement;
        if (table) {
          const headers = Array.from(table.querySelectorAll('th.day-header .day-name'));
          const colIndex = headers.findIndex(h => h.textContent?.toLowerCase().includes(dia));
          if (colIndex !== -1) {
            const rows = Array.from(table.querySelectorAll('tbody tr'));
            const row = rows.find(r => {
              const timeCell = r.querySelector('.time-mark');
              return timeCell && timeCell.textContent?.startsWith(hora + ':00');
            });
            if (row) {
              const targetCell = row.children[colIndex + 1];
              if (targetCell) {
                const radio = targetCell.querySelector('input[type="radio"]') as HTMLInputElement;
                if (radio && !radio.disabled) {
                  radio.click();
                  this.commandDetected.next(`Horario seleccionado: ${dia} a las ${hora}:00`);
                } else {
                  this.commandDetected.next('Horario no disponible u ocupado');
                }
              }
            }
          }
        }
      }
    }
    // INGRESAR MOTIVO
    else if (cleanText.includes('ingresar motivo')) {
      const textarea = document.querySelector('textarea') as HTMLTextAreaElement;
      if (textarea) {
        textarea.focus();
        this.commandDetected.next('Motivo enfocado');
      }
    }
    // PAGOS
    else if (cleanText.includes('seleccionar tarjeta') || cleanText.includes('pago con tarjeta')) {
      const div = Array.from(document.querySelectorAll('div, button, a')).find(d => d.textContent?.toLowerCase().includes('pago seguro con tarjeta') || (d.textContent?.toLowerCase().includes('tarjeta') && d.textContent?.toLowerCase().includes('visa')));
      if (div) { (div as HTMLElement).click(); this.commandDetected.next('Pago con tarjeta seleccionado'); }
    }
    else if (cleanText.includes('seleccionar efectivo') || cleanText.includes('pago en efectivo')) {
      const div = Array.from(document.querySelectorAll('div, button, a')).find(d => d.textContent?.toLowerCase().includes('pago en efectivo') && d.textContent?.toLowerCase().includes('caja'));
      if (div) { (div as HTMLElement).click(); this.commandDetected.next('Pago en efectivo seleccionado'); }
    }
    // BOTONES MODAL (Confirmar / Cancelar) Y SUBMIT GENERAL
    else if (cleanText.includes('confirmar reserva') || cleanText.includes('confirmar cita') || cleanText.includes('enviar solicitud')) {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.toLowerCase().includes('confirmar') || b.textContent?.toLowerCase().includes('enviar solicitud'));
      if (btn) { btn.click(); this.commandDetected.next('Confirmando...'); }
    }
    else if (cleanText.includes('cancelar') || cleanText.includes('cerrar modal') || cleanText.includes('cerrar')) {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.toLowerCase().includes('cerrar') || b.textContent?.toLowerCase().includes('cancelar'));
      if (btn) { btn.click(); this.commandDetected.next('Cerrando/Cancelando...'); }
    }
    // BOTÓN DE ACCIÓN (SUBMIT) GENÉRICO
    else if (cleanText.includes('pagar ahora') || cleanText.includes('finalizar registro') || cleanText.includes('entrar') || cleanText.includes('ingresar') || cleanText.includes('iniciar sesión') || cleanText.includes('iniciar sesion')) {
      const btnSubmit = document.querySelector('button[type="submit"], button.btn-primary, button.btn-success') as HTMLButtonElement;
      if (btnSubmit) {
        btnSubmit.click();
        this.commandDetected.next('Ejecutando acción principal...');
      }
    }
    // NAVEGAR ENTRE CAMPOS (SIN MOUSE)
    else if (cleanText.includes('siguiente campo') || cleanText.includes('pasar al siguiente')) {
      this.focusNextElement(1);
    }
    else if (cleanText.includes('campo anterior') || cleanText.includes('volver al anterior')) {
      this.focusNextElement(-1);
    }
    // LIMPIAR CAMPO
    else if (cleanText.includes('limpiar campo') || cleanText.includes('borrar todo')) {
      const activeElement = document.activeElement as HTMLInputElement;
      if (activeElement && activeElement.value !== undefined) {
        activeElement.value = '';
        activeElement.dispatchEvent(new Event('input', { bubbles: true }));
        this.commandDetected.next('Campo limpiado');
      }
    }

    // ESCRIBIR EN FORMULARIOS
    if (cleanText.startsWith('escribe ') || cleanText.startsWith('escribir ') || cleanText.startsWith('poner ') || cleanText.startsWith('pon ')) {
      const activeElement = document.activeElement as HTMLInputElement;
      if (activeElement && activeElement.value !== undefined) {
        
        // Usamos el texto original (sin comas ni signos, pero preservando puntos reales)
        let originalTextLow = text.toLowerCase().trim();
        
        // Quitar la palabra clave del inicio
        let rawValue = originalTextLow;
        if (rawValue.startsWith('escribir ')) rawValue = rawValue.replace('escribir ', '');
        else if (rawValue.startsWith('escribe ')) rawValue = rawValue.replace('escribe ', '');
        else if (rawValue.startsWith('poner ')) rawValue = rawValue.replace('poner ', '');
        else if (rawValue.startsWith('pon ')) rawValue = rawValue.replace('pon ', '');
        
        // Limpiamos solo el punto final de la oración que suele poner Deepgram
        rawValue = rawValue.trim();
        if (rawValue.endsWith('.')) {
          rawValue = rawValue.substring(0, rawValue.length - 1);
        }

        // Detectar modificadores de formato dictados por el usuario
        let isTodoJunto = false;
        let isMayuscula = false;

        if (rawValue.includes('todo junto') || rawValue.includes('sin espacios')) {
          isTodoJunto = true;
          rawValue = rawValue.replace(/todo junto/g, '').replace(/sin espacios/g, '').trim();
        }

        if (rawValue.includes('mayúscula') || rawValue.includes('mayuscula')) {
          isMayuscula = true;
          rawValue = rawValue.replace(/con mayúscula/g, '')
                             .replace(/en mayúscula/g, '')
                             .replace(/mayúscula inicial/g, '')
                             .replace(/mayúscula/g, '')
                             .replace(/con mayuscula/g, '')
                             .replace(/en mayuscula/g, '')
                             .replace(/mayuscula inicial/g, '')
                             .replace(/mayuscula/g, '')
                             .trim();
          if (rawValue.endsWith(' con')) rawValue = rawValue.substring(0, rawValue.length - 4);
          if (rawValue.endsWith(' en')) rawValue = rawValue.substring(0, rawValue.length - 3);
          rawValue = rawValue.trim();
        }

        // Traducir palabras dictadas a símbolos
        let finalValue = rawValue
          .replace(/ arroba /g, '@')
          .replace(/ punto /g, '.')
          .replace(/ guión bajo /g, '_')
          .replace(/ guion bajo /g, '_')
          .replace(/ guión /g, '-')
          .replace(/ guion /g, '-');
          
        // Formateos automáticos (quitar espacios si es contraseña, si tiene @, o si el usuario lo pidió)
        if (finalValue.includes('@') || isTodoJunto || activeElement.type === 'password') {
          finalValue = finalValue.replace(/\s+/g, '');
        }

        // Formateo de mayúscula inicial
        if (isMayuscula && finalValue.length > 0) {
          finalValue = finalValue.charAt(0).toUpperCase() + finalValue.slice(1);
        }

        activeElement.value = finalValue;
        activeElement.dispatchEvent(new Event('input', { bubbles: true }));
        this.commandDetected.next(`Texto escrito: ${finalValue}`);
      }
    }
  }


  private inyectarTexto(texto: string) {
    const activeElement = document.activeElement as HTMLInputElement | HTMLTextAreaElement;
    if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
      const start = activeElement.selectionStart || 0;
      const end = activeElement.selectionEnd || 0;
      const val = activeElement.value;

      activeElement.value = val.substring(0, start) + texto + val.substring(end);

      activeElement.dispatchEvent(new Event('input', { bubbles: true }));
      this.commandDetected.next(`Escribiendo: ${texto}`);
    }
  }

  private focusNextElement(direction: number) {
    // Filtramos solo campos de formulario reales (ignorando botones o inputs ocultos)
    const focusableElements = Array.from(document.querySelectorAll(
      'input:not([disabled]):not([type="hidden"]):not([type="submit"]):not([type="button"]), textarea:not([disabled]), select:not([disabled])'
    )) as HTMLElement[];
    
    if (focusableElements.length === 0) return;

    const currentIndex = focusableElements.findIndex(el => el === document.activeElement);
    
    let nextIndex = 0;
    if (currentIndex !== -1) {
      nextIndex = currentIndex + direction;
      // Navegación circular
      if (nextIndex >= focusableElements.length) nextIndex = 0;
      if (nextIndex < 0) nextIndex = focusableElements.length - 1;
    }

    focusableElements[nextIndex].focus();
    this.commandDetected.next(direction > 0 ? 'Siguiente campo' : 'Campo anterior');
  }

  ngOnDestroy() {
    this.stopListening();
  }
}
