import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class RecaptchaService {
  private readonly siteKey = '6Ld0RgEuAAAAAIwlBueBWFXShFduB1v-sXijBoSQ';
  private scriptCargado = false;
  private cargandoPromise: Promise<void> | null = null;

  cargarScript(): Promise<void> {
    if (this.scriptCargado && typeof window !== 'undefined' && (window as any).grecaptcha) {
      return Promise.resolve();
    }
    if (this.cargandoPromise) {
      return this.cargandoPromise;
    }
    this.cargandoPromise = new Promise<void>((resolve, reject) => {
      if (typeof document === 'undefined') {
        resolve();
        return;
      }
      const existing = document.querySelector('script[src*="recaptcha/api.js"]');
      if (existing) {
        this.scriptCargado = true;
        resolve();
        return;
      }
      const script = document.createElement('script');
      script.src = `https://www.google.com/recaptcha/api.js?render=${this.siteKey}`;
      script.async = true;
      script.defer = true;
      script.onload = () => {
        this.scriptCargado = true;
        resolve();
      };
      script.onerror = () => {
        this.cargandoPromise = null;
        reject(new Error('Error al cargar reCAPTCHA'));
      };
      document.head.appendChild(script);
    });
    return this.cargandoPromise;
  }

  obtenerToken(action: string = 'registro'): Promise<string> {
    return this.cargarScript().then(() => {
      return new Promise<string>((resolve, reject) => {
        const grecaptcha = typeof window !== 'undefined' ? (window as any).grecaptcha : null;
        if (!grecaptcha || !grecaptcha.ready) {
          reject(new Error('reCAPTCHA no disponible'));
          return;
        }
        grecaptcha.ready(() => {
          grecaptcha.execute(this.siteKey, { action })
            .then((token: string) => resolve(token))
            .catch((err: any) => reject(err));
        });
      });
    });
  }
}
