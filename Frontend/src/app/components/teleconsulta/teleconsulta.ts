import { Component, OnDestroy, OnInit, ChangeDetectorRef, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../services/notification.service';
import AgoraRTC, { IAgoraRTCClient, ICameraVideoTrack, IMicrophoneAudioTrack, IAgoraRTCRemoteUser } from 'agora-rtc-sdk-ng';

import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-teleconsulta',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './teleconsulta.html',
  styleUrls: ['./teleconsulta.css']
})
export class TeleconsultaComponent implements OnInit, OnDestroy {

  citaId: string = '';
  rol: string = 'PACIENTE'; 
  uidLocal: string = '';

  unido: boolean = false;
  micOn: boolean = true;
  camOn: boolean = true;
  localSpeaking: boolean = false;
  
  remoteUsers: IAgoraRTCRemoteUser[] = [];
  activeSpeakers: Set<string> = new Set();

  mostrarSubtitulos: boolean = false;
  deepgramActive: boolean = false;
  mensajeActualEmisor: string = '';
  mensajeActualTranscrito: string = '';

  private rtcClient!: IAgoraRTCClient;
  private localAudioTrack!: IMicrophoneAudioTrack;
  private localVideoTrack!: ICameraVideoTrack;

  private agoraAppId: string = '';
  private deepgramApiKey: string = '';
  private deepgramSocket: any = null;
  private mediaRecorder: MediaRecorder | null = null;
  private apiUrl = environment.apiUrl;

  private infoIntervalId: any;
  nombreLocal: string = '';
  nombresRemotos: { [uid: string]: string } = {};

  // Chat y Grid properties
  isChatOpen: boolean = false;
  mensajesChat: any[] = [];
  nuevoMensaje: string = '';
  @ViewChild('chatContainer') chatContainer!: ElementRef;

  // Device selection properties
  microphones: MediaDeviceInfo[] = [];
  cameras: MediaDeviceInfo[] = [];
  selectedMicId: string = '';
  selectedCamId: string = '';
  showMicMenu: boolean = false;
  showCamMenu: boolean = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private ns: NotificationService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.citaId = this.route.snapshot.paramMap.get('id') || '0';
    const usuarioString = localStorage.getItem('usuario');
    if (usuarioString) {
      const u = JSON.parse(usuarioString);
      this.rol = u.rol;
      this.nombreLocal = u.nombre + " " + (u.apellido || "");
    }
    this.obtenerConfiguracion();
  }

  private agoraToken: string | null = null;

  obtenerConfiguracion() {
    const canalUID = `cita-${this.citaId}`;
    const token = localStorage.getItem('token');
    const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};
    this.http.get<any>(`${this.apiUrl}/api/teleconsulta/config?canal=${canalUID}&rol=${this.rol}`, { headers }).subscribe({
      next: (config) => {
        this.agoraAppId = config.agoraAppId;
        this.deepgramApiKey = config.deepgramApiKey;
        this.agoraToken = config.agoraToken || null;
        this.prepararAgora().then(() => {
           // Auto-entrar a la sala
           this.unirseASala();
        });
      },
      error: (err) => console.error("Error al obtener credenciales", err)
    });
  }

  async prepararAgora() {
    this.rtcClient = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' });

    // Habilitar indicador de volumen de voz (cada 200ms)
    this.rtcClient.enableAudioVolumeIndicator();

    this.rtcClient.on("volume-indicator", volumes => {
      this.activeSpeakers.clear();
      let localIsSpeaking = false;
      volumes.forEach((volume) => {
        // Aumentado a 15 para evitar falsos positivos con ruido de fondo
        if (volume.level > 15) { 
          if (volume.uid === this.rtcClient.uid) {
             localIsSpeaking = true;
          } else {
             this.activeSpeakers.add(volume.uid.toString());
          }
        }
      });
      this.localSpeaking = localIsSpeaking;
      // Forzar actualización de Angular para limpiar o añadir el borde
      this.cdr.detectChanges();
    });

    this.rtcClient.on('user-joined', (user) => {
      if (!this.remoteUsers.find(u => u.uid === user.uid)) {
        this.remoteUsers.push(user);
      }
    });

    this.rtcClient.on('user-published', async (user, mediaType) => {
      await this.rtcClient.subscribe(user, mediaType);
      
      if (!this.remoteUsers.find(u => u.uid === user.uid)) {
        this.remoteUsers.push(user);
      }

      if (mediaType === 'video') {
        const remoteVideoTrack = user.videoTrack;
        setTimeout(() => {
          remoteVideoTrack?.play(`remote-video-${user.uid}`);
        }, 100);
      }

      if (mediaType === 'audio') {
        const remoteAudioTrack = user.audioTrack;
        remoteAudioTrack?.play();
      }
    });

    this.rtcClient.on('user-unpublished', (user, mediaType) => {
      if (mediaType === 'video') {
        user.videoTrack?.stop();
      }
      if (mediaType === 'audio') {
        user.audioTrack?.stop();
      }
    });

    this.rtcClient.on('user-left', (user) => {
      this.remoteUsers = this.remoteUsers.filter(u => u.uid !== user.uid);
      this.activeSpeakers.delete(user.uid.toString());
      delete this.nombresRemotos[user.uid.toString()];
    });

    this.rtcClient.on('stream-message', (uid, payload) => {
      const texto = new TextDecoder().decode(payload);
      try {
        const data = JSON.parse(texto);
        if (data.type === 'INFO') {
           this.nombresRemotos[uid.toString()] = `${data.rol} (${data.nombre})`;
           this.cdr.detectChanges();
        } else if (data.type === 'CHAT') {
           this.mensajesChat.push(data);
           this.cdr.detectChanges();
           this.scrollToBottom();
           if (!this.isChatOpen) {
              this.ns.success(`Nuevo mensaje de ${data.nombre}`);
           }
        } else if (data.type === 'SUBTITULO') {
           this.recibirSubtitulo(data.texto, data.emisor);
        }
      } catch(e) {
        // Fallback for raw text
        this.recibirSubtitulo(texto);
      }
    });
  }

  async unirseASala() {
    if (!this.agoraAppId || this.agoraAppId === 'AGORA_NOT_FOUND') {
      this.ns.error('Error de Configuración: Falta el Agora App ID en el backend.');
      return;
    }

    try {
      const canalUID = `cita-${this.citaId}`;
      const uid = await this.rtcClient.join(this.agoraAppId, canalUID, this.agoraToken, null);
      this.uidLocal = uid.toString();

      let tracksToPublish = [];

      try {
        this.localAudioTrack = await AgoraRTC.createMicrophoneAudioTrack();
        tracksToPublish.push(this.localAudioTrack);
      } catch(e) {
        console.warn('No se pudo obtener acceso al micrófono', e);
        this.micOn = false;
      }

      try {
        this.localVideoTrack = await AgoraRTC.createCameraVideoTrack();
        tracksToPublish.push(this.localVideoTrack);
      } catch(e) {
        console.warn('No se pudo obtener acceso a la cámara', e);
        this.camOn = false;
      }

      await this.loadDevices();

      this.unido = true;
      
      if (this.localVideoTrack) {
        setTimeout(() => {
          this.localVideoTrack.play('local-video');
        }, 100);
      }

      if (tracksToPublish.length > 0) {
        await this.rtcClient.publish(tracksToPublish as any);
      } else {
        this.ns.error('No se pudo acceder ni a tu cámara ni a tu micrófono. Has entrado como espectador.');
      }

      // Iniciar el broadcast de nuestra info (para que otros sepan nuestro nombre)
      this.infoIntervalId = setInterval(() => {
        if (this.unido && this.rtcClient && this.rtcClient.connectionState === 'CONNECTED') {
           const msg = JSON.stringify({ type: 'INFO', rol: this.rol, nombre: this.nombreLocal });
           const encoded = new TextEncoder().encode(msg);
           try {
             (this.rtcClient as any).sendStreamMessage({ payload: encoded, syncWithAudio: false });
           } catch(e) {}
        }
      }, 3000);

    } catch (e) {
      console.error('Fallo al entrar a sala', e);
      this.ns.error('Error de conexión. Asegúrate de tener permisos de cámara y micrófono.');
    }
  }

  async toggleMic() {
    if(this.localAudioTrack) {
      this.micOn = !this.micOn;
      await this.localAudioTrack.setEnabled(this.micOn);
    }
  }

  async toggleCam() {
    if(this.localVideoTrack) {
      this.camOn = !this.camOn;
      await this.localVideoTrack.setEnabled(this.camOn);
    }
  }

  async loadDevices() {
    try {
      this.cameras = await AgoraRTC.getCameras();
      this.microphones = await AgoraRTC.getMicrophones();
      
      // Intentar obtener el ID activo actual o usar el primero
      this.selectedMicId = this.localAudioTrack?.getTrackLabel() || this.microphones[0]?.deviceId || '';
      this.selectedCamId = this.localVideoTrack?.getTrackLabel() || this.cameras[0]?.deviceId || '';
    } catch(e) {
      console.warn('No se pudieron cargar los dispositivos', e);
    }
  }

  onRightClickMic(event: MouseEvent) {
    event.preventDefault();
    this.showMicMenu = !this.showMicMenu;
    this.showCamMenu = false;
  }

  onRightClickCam(event: MouseEvent) {
    event.preventDefault();
    this.showCamMenu = !this.showCamMenu;
    this.showMicMenu = false;
  }

  async changeAudioDevice(deviceId: string) {
    if (this.localAudioTrack) {
      await this.localAudioTrack.setDevice(deviceId);
      this.selectedMicId = deviceId;
    }
    this.showMicMenu = false;
  }

  async changeVideoDevice(deviceId: string) {
    if (this.localVideoTrack) {
      await this.localVideoTrack.setDevice(deviceId);
      this.selectedCamId = deviceId;
    }
    this.showCamMenu = false;
  }

  async salirDeSala() {
    this.localAudioTrack?.close();
    this.localVideoTrack?.close();
    await this.rtcClient?.leave();
    this.ngOnDestroy();
    
    if (this.rol === 'MEDICO') {
      this.router.navigate(['/medico/agenda']);
    } else {
      this.router.navigate(['/paciente/mis-citas']);
    }
  }

  isSpeaking(uid: any): boolean {
    return this.activeSpeakers.has(uid.toString());
  }

  getGridClass(): string {
    const total = 1 + this.remoteUsers.length;
    if (total === 1) return 'grid-1';
    if (total === 2) return 'grid-2';
    if (total === 3) return 'grid-3';
    if (total === 4) return 'grid-4';
    if (total >= 5) return 'grid-5';
    return '';
  }

  toggleChat() {
    this.isChatOpen = !this.isChatOpen;
    if (this.isChatOpen) {
      setTimeout(() => this.scrollToBottom(), 100);
    }
  }

  enviarMensaje() {
    if (!this.nuevoMensaje.trim()) return;
    const msgObj = {
      type: 'CHAT',
      uid: this.uidLocal,
      nombre: `${this.rol} - ${this.nombreLocal}`,
      texto: this.nuevoMensaje.trim()
    };
    
    // Add locally
    this.mensajesChat.push(msgObj);
    this.nuevoMensaje = '';
    
    // Broadcast
    try {
      if (this.rtcClient && this.rtcClient.connectionState === 'CONNECTED') {
        const payload = JSON.stringify(msgObj);
        const encoded = new TextEncoder().encode(payload);
        (this.rtcClient as any).sendStreamMessage({ payload: encoded, syncWithAudio: false });
      }
    } catch(e) {}
    
    setTimeout(() => this.scrollToBottom(), 100);
  }

  scrollToBottom() {
    try {
      if (this.chatContainer) {
        this.chatContainer.nativeElement.scrollTop = this.chatContainer.nativeElement.scrollHeight;
      }
    } catch(err) {}
  }

  async toggleDeepgram() {
    if(!this.deepgramActive) {
      this.mostrarSubtitulos = true;
      this.iniciarReconocimientoVoz();
    } else {
      this.mostrarSubtitulos = false;
      this.detenerReconocimientoVoz();
    }
  }

  async iniciarReconocimientoVoz() {
    if (!this.deepgramApiKey) {
      this.ns.error('La clave de API de Deepgram no está configurada en el servidor.');
      return;
    }
    
    this.deepgramActive = true;

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });

      const deepgramUrl = 'wss://api.deepgram.com/v1/listen?model=nova-2&language=es-419&smart_format=true';
      this.deepgramSocket = new WebSocket(deepgramUrl, ['token', this.deepgramApiKey]);

      this.deepgramSocket.onopen = () => {
        console.log('Deepgram conectado');

        this.mediaRecorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
        this.mediaRecorder.ondataavailable = (e) => {
          // Solo enviamos audio a Deepgram si el micrófono de Agora no está silenciado
          if (this.micOn && e.data.size > 0 && this.deepgramSocket && this.deepgramSocket.readyState === 1) {
            this.deepgramSocket.send(e.data);
          }
        };
        this.mediaRecorder.start(250); 
      };

      this.deepgramSocket.onmessage = (event: any) => {
        const data = JSON.parse(event.data);
        if (data.channel && data.channel.alternatives && data.channel.alternatives[0]) {
            const transcripcion = data.channel.alternatives[0].transcript;
            if (transcripcion && transcripcion.trim().length > 0) {
                if (data.is_final) {
                    this.enviarSubtituloAlPaciente(transcripcion);
                } else {
                    // Mostrar resultados intermedios de forma fluida (solo para el que habla, para no saturar la red)
                    this.recibirSubtitulo(transcripcion, "Tú");
                }
            }
        }
      };

      this.deepgramSocket.onerror = (e: any) => {
        console.error('WebSocket Error:', e);
        this.ns.error('Fallo en la conexión con el servidor de subtítulos.');
        this.detenerReconocimientoVoz();
      };

    } catch (e) {
      console.error('Error con Deepgram', e);
      this.deepgramActive = false;
      this.mostrarSubtitulos = false;
      this.ns.error('Error al iniciar el micrófono para subtítulos.');
    }
  }

  detenerReconocimientoVoz() {
    this.deepgramActive = false;
    this.mostrarSubtitulos = false;
    if(this.mediaRecorder) {
        this.mediaRecorder.stop();
    }
    if(this.deepgramSocket) {
        this.deepgramSocket.close();
    }
  }

  getInitials(name: string): string {
    if (!name) return 'U';
    // Si tiene el formato ROL (Nombre), extraemos el nombre. Ej: "MEDICO (Juan)" -> "Juan"
    let cleanName = name;
    const match = name.match(/\((.*?)\)/);
    if (match && match[1]) {
      cleanName = match[1];
    }
    const words = cleanName.trim().split(' ').filter(w => w.length > 0);
    if (words.length >= 2) {
       return (words[0][0] + words[1][0]).toUpperCase();
    }
    return cleanName.substring(0, 2).toUpperCase();
  }

  enviarSubtituloAlPaciente(texto: string) {
      console.log('Emitiendo:', texto);
      try {
        const msgObj = {
           type: 'SUBTITULO',
           texto: texto,
           emisor: `${this.rol} (${this.nombreLocal})`
        };
        
        if (this.rtcClient && this.rtcClient.connectionState === 'CONNECTED') {
          const encoded = new TextEncoder().encode(JSON.stringify(msgObj));
          (this.rtcClient as any).sendStreamMessage({ payload: encoded, syncWithAudio: false });
        }
        this.recibirSubtitulo(texto, "Tú");
      } catch(e) {
        console.warn("Fallo envio Agora, mostrando localmente: ", e);
      }
  }

  recibirSubtitulo(texto: string, emisor: string = "Médico") {
      if (!this.mostrarSubtitulos) return; // Master Switch Check

      this.mensajeActualEmisor = emisor;
      this.mensajeActualTranscrito = texto;
      this.cdr.detectChanges();

      setTimeout(() => {
          if(this.mensajeActualTranscrito === texto) {
              this.mensajeActualTranscrito = '';
              this.cdr.detectChanges();
          }
      }, 5000);
  }

  ngOnDestroy() {
    this.detenerReconocimientoVoz();
    if(this.localAudioTrack) {
        this.localAudioTrack.close();
    }
    if(this.localVideoTrack) {
        this.localVideoTrack.close();
    }
    if(this.rtcClient) {
        this.rtcClient.leave();
    }
  }

}
