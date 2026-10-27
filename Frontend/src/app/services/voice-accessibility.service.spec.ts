import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { VoiceAccessibilityService } from './voice-accessibility.service';

class FakeRecognition {
  static instances: FakeRecognition[] = [];
  lang = '';
  continuous = false;
  interimResults = true;
  onstart: (() => void) | null = null;
  onend: (() => void) | null = null;
  onerror: ((event: { error: string }) => void) | null = null;
  onresult: ((event: any) => void) | null = null;
  start = jasmine.createSpy('start');
  abort = jasmine.createSpy('abort');
  constructor() { FakeRecognition.instances.push(this); }
}

describe('Public voice accessibility', () => {
  let service: VoiceAccessibilityService;
  let http: jasmine.SpyObj<HttpClient>;
  let router: jasmine.SpyObj<Router>;
  let original: PropertyDescriptor | undefined;
  let originalWebkit: PropertyDescriptor | undefined;

  beforeEach(() => {
    original = Object.getOwnPropertyDescriptor(window, 'SpeechRecognition');
    originalWebkit = Object.getOwnPropertyDescriptor(window, 'webkitSpeechRecognition');
    Object.defineProperty(window, 'SpeechRecognition', { configurable: true, value: FakeRecognition });
    Object.defineProperty(window, 'webkitSpeechRecognition', { configurable: true, value: undefined });
    FakeRecognition.instances = [];
    http = jasmine.createSpyObj('HttpClient', ['get']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    service = new VoiceAccessibilityService(http, router);
    jasmine.clock().install();
  });

  afterEach(() => {
    service.ngOnDestroy();
    jasmine.clock().uninstall();
    if (original) Object.defineProperty(window, 'SpeechRecognition', original);
    else Reflect.deleteProperty(window, 'SpeechRecognition');
    if (originalWebkit) Object.defineProperty(window, 'webkitSpeechRecognition', originalWebkit);
    else Reflect.deleteProperty(window, 'webkitSpeechRecognition');
  });

  it('starts Spanish public recognition without requesting credentials and avoids duplicate starts', () => {
    service.startListening();
    service.startListening();
    const recognition = FakeRecognition.instances[0];
    expect(FakeRecognition.instances.length).toBe(1);
    expect(recognition.lang).toBe('es-ES');
    expect(recognition.continuous).toBeTrue();
    expect(recognition.interimResults).toBeFalse();
    expect(recognition.start).toHaveBeenCalledTimes(1);
    expect(http.get).not.toHaveBeenCalled();
    recognition.onstart?.();
    expect(service.isListening).toBeTrue();
    expect(document.body.classList.contains('voice-active')).toBeTrue();
  });

  it('supports the webkit-prefixed API', () => {
    Object.defineProperty(window, 'SpeechRecognition', { configurable: true, value: undefined });
    Object.defineProperty(window, 'webkitSpeechRecognition', { configurable: true, value: FakeRecognition });
    service.startListening();
    expect(FakeRecognition.instances[0].start).toHaveBeenCalled();
    expect(http.get).not.toHaveBeenCalled();
  });

  it('reports unsupported browsers through the existing message contract', () => {
    Object.defineProperty(window, 'SpeechRecognition', { configurable: true, value: undefined });
    const message = jasmine.createSpy('message');
    service.commandDetected.subscribe(message);
    service.startListening();
    expect(service.isListening).toBeFalse();
    expect(service.errorMessage).toContain('no admite');
    expect(message).toHaveBeenCalledWith(service.errorMessage);
    expect(http.get).not.toHaveBeenCalled();
  });

  it('dispatches final commands only, preserving navigation and command messages', () => {
    const message = jasmine.createSpy('message');
    service.commandDetected.subscribe(message);
    service.startListening();
    const recognition = FakeRecognition.instances[0];
    recognition.onresult?.({ resultIndex: 0, results: [{ isFinal: false, 0: { transcript: 'ir a inicio' } }] });
    expect(router.navigate).not.toHaveBeenCalled();
    recognition.onresult?.({ resultIndex: 1, results: [
      { isFinal: true, 0: { transcript: 'ir a registro' } },
      { isFinal: true, 0: { transcript: 'Ir a inicio.' } }
    ] });
    expect(router.navigate).toHaveBeenCalledOnceWith(['/']);
    expect(message).toHaveBeenCalledWith('Navegando al Inicio');
  });

  it('stops and ignores late callbacks without restarting the microphone', () => {
    service.startListening();
    const recognition = FakeRecognition.instances[0];
    const lateStart = recognition.onstart;
    const lateResult = recognition.onresult;
    recognition.onend?.();
    service.stopListening();
    lateStart?.();
    lateResult?.({ resultIndex: 0, results: [{ isFinal: true, 0: { transcript: 'ir a inicio' } }] });
    jasmine.clock().tick(1000);
    expect(recognition.abort).toHaveBeenCalledTimes(1);
    expect(recognition.start).toHaveBeenCalledTimes(1);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(service.isListening).toBeFalse();
    expect(document.body.classList.contains('voice-active')).toBeFalse();
  });

  it('reports microphone denial and network errors without endless retries', () => {
    for (const error of ['not-allowed', 'network']) {
      service.startListening();
      const recognition = FakeRecognition.instances.at(-1)!;
      recognition.onerror?.({ error });
      jasmine.clock().tick(1000);
      expect(service.isListening).toBeFalse();
      expect(service.errorMessage).not.toBe('');
      expect(recognition.abort).toHaveBeenCalledTimes(1);
      expect(recognition.start).toHaveBeenCalledTimes(1);
    }
  });

  it('bounds automatic restarts when the browser repeatedly ends recognition', () => {
    service.startListening();
    const recognition = FakeRecognition.instances[0];
    for (let i = 0; i < 4; i++) {
      recognition.onend?.();
      jasmine.clock().tick(300);
    }
    expect(recognition.start).toHaveBeenCalledTimes(4);
    expect(service.isListening).toBeFalse();
    expect(service.errorMessage).toContain('detuvo');
  });

  it('cleans up when the browser throws while starting', () => {
    service.startListening();
    const recognition = FakeRecognition.instances[0];
    recognition.start.and.throwError('unavailable');
    recognition.onend?.();
    jasmine.clock().tick(300);
    expect(service.isListening).toBeFalse();
    expect(service.errorMessage).toContain('reanudar');
    expect(recognition.abort).toHaveBeenCalled();
  });
});
