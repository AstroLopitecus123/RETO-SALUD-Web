import { AfterViewInit, Directive, OnDestroy } from '@angular/core';

/** Covers routed overlays without requiring changes to their owning templates. */
@Directive({ selector: '[appModalManager]', standalone: true })
export class ModalManagerDirective implements AfterViewInit, OnDestroy {
  private observer?: MutationObserver;
  private stack: { element: HTMLElement; returnFocus: HTMLElement | null }[] = [];
  private inerted = new Map<HTMLElement, boolean>();
  private attributes = new Map<HTMLElement, Map<string, string | null>>();
  private overflow?: string;
  private selector = '.glass-overlay, .acc-test-overlay, [data-managed-modal]';

  ngAfterViewInit() {
    this.observer = new MutationObserver(() => this.sync());
    this.observer.observe(document.body, { childList: true, subtree: true, attributes: true,
      attributeFilter: ['class', 'hidden', 'data-managed-modal'] });
    document.addEventListener('keydown', this.onKey, true);
    document.addEventListener('focusin', this.onFocus, true);
    this.sync();
  }

  private focusables(element: HTMLElement): HTMLElement[] {
    return Array.from(element.querySelectorAll<HTMLElement>('button, a[href], input, select, textarea, [tabindex]'))
      .filter(el => el.tabIndex >= 0 && !el.matches(':disabled') && !el.closest('[inert], [hidden]') && el.getClientRects().length > 0);
  }

  private focus(element: HTMLElement) {
    const preferred = element.querySelector<HTMLElement>('[data-modal-initial-focus]');
    (preferred || this.focusables(element)[0] || element).focus({ preventScroll: true });
  }

  private restoreBackground() {
    this.inerted.forEach((value, element) => element.inert = value);
    this.inerted.clear();
  }

  private sync() {
    this.restoreBackground();
    const elements = Array.from(document.querySelectorAll<HTMLElement>(this.selector))
      .filter(el => !el.hidden && getComputedStyle(el).display !== 'none' && getComputedStyle(el).visibility !== 'hidden');
    const previous = this.stack.at(-1);
    const removed = this.stack.filter(entry => !elements.includes(entry.element));
    removed.forEach(({ element }) => {
      this.attributes.get(element)?.forEach((value, key) => {
        if (value === null) element.removeAttribute(key);
        else element.setAttribute(key, value);
      });
      this.attributes.delete(element);
    });
    this.stack = this.stack.filter(entry => elements.includes(entry.element));
    elements.forEach(element => {
      if (!this.stack.some(entry => entry.element === element)) {
        this.attributes.set(element, new Map(['role', 'aria-modal', 'tabindex', 'aria-label'].map(key => [key, element.getAttribute(key)])));
        element.setAttribute('role', element.getAttribute('role') || 'dialog');
        element.setAttribute('aria-modal', 'true');
        if (!element.hasAttribute('tabindex')) element.tabIndex = -1;
        if (!element.hasAttribute('aria-label') && !element.hasAttribute('aria-labelledby')) {
          element.setAttribute('aria-label', element.querySelector('h2, h3')?.textContent?.trim() || 'Diálogo');
        }
        const active = document.activeElement as HTMLElement;
        const replaced = removed.find(entry => entry.element.contains(active));
        this.stack.push({ element, returnFocus: replaced ? replaced.returnFocus : active });
      }
    });
    // The security warning must stay above any existing dialog.
    this.stack.sort((a, b) => Number(a.element.hasAttribute('data-session-warning')) - Number(b.element.hasAttribute('data-session-warning')) ||
      (parseInt(getComputedStyle(a.element).zIndex, 10) || 0) - (parseInt(getComputedStyle(b.element).zIndex, 10) || 0));
    const top = this.stack.at(-1)?.element;
    if (top) {
      if (this.overflow === undefined) {
        this.overflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
      }
      let branch: HTMLElement = top;
      while (branch.parentElement) {
        Array.from(branch.parentElement.children).forEach(sibling => {
          if (sibling !== branch && sibling instanceof HTMLElement) {
            if (top.id === 'dashboard-sidebar' && sibling.classList.contains('sidebar-backdrop')) return;
            this.inerted.set(sibling, sibling.inert);
            sibling.inert = true;
          }
        });
        branch = branch.parentElement;
        if (branch === document.body) break;
      }
      const returning = removed[0]?.returnFocus;
      if (returning?.isConnected && top.contains(returning) && !returning.closest('[inert], [hidden]')) returning.focus({ preventScroll: true });
      else if (previous?.element !== top || !top.contains(document.activeElement)) this.focus(top);
    } else {
      if (this.overflow !== undefined) document.body.style.overflow = this.overflow;
      this.overflow = undefined;
      const target = removed[0]?.returnFocus;
      if (target?.isConnected && !target.closest('[inert], [hidden]')) target.focus({ preventScroll: true });
    }
  }

  private onFocus = (event: FocusEvent) => {
    const top = this.stack.at(-1)?.element;
    if (top && !top.contains(event.target as Node)) this.focus(top);
  };

  private onKey = (event: KeyboardEvent) => {
    const top = this.stack.at(-1)?.element;
    if (!top) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopImmediatePropagation();
      // Never let Escape implicitly renew or dismiss an inactivity warning.
      if (!top.hasAttribute('data-session-warning')) {
        top.querySelector<HTMLElement>('[data-modal-close], .btn-close-glass, .btn-test-cancel')?.click();
      }
    } else if (event.key === 'Tab') {
      const items = this.focusables(top);
      const index = items.indexOf(document.activeElement as HTMLElement);
      if (!items.length || (event.shiftKey ? index <= 0 : index < 0 || index === items.length - 1)) {
        event.preventDefault();
        (event.shiftKey ? items.at(-1) || top : items[0] || top).focus();
      }
    }
  };

  ngOnDestroy() {
    this.observer?.disconnect();
    document.removeEventListener('keydown', this.onKey, true);
    document.removeEventListener('focusin', this.onFocus, true);
    this.restoreBackground();
    this.attributes.forEach((attributes, element) => attributes.forEach((value, key) => {
      if (value === null) element.removeAttribute(key);
      else element.setAttribute(key, value);
    }));
    this.attributes.clear();
    if (this.overflow !== undefined) document.body.style.overflow = this.overflow;
  }
}
