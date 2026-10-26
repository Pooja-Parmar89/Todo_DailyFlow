import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmDialog, ToastHost } from './shared/feedback';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHost, ConfirmDialog],
  template: `<router-outlet /><app-toasts /><app-confirm />`,
})
export class App {}
