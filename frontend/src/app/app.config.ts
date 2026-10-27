import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import { provideEchartsCore } from 'ngx-echarts';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { activityInterceptor, errorToastInterceptor } from './core/api/interceptors';

const TraceflowPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{blue.50}', 100: '{blue.100}', 200: '{blue.200}', 300: '{blue.300}', 400: '{blue.400}',
      500: '{blue.500}', 600: '{blue.600}', 700: '{blue.700}', 800: '{blue.800}', 900: '{blue.900}', 950: '{blue.950}'
    }
  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(withFetch(), withInterceptors([activityInterceptor, errorToastInterceptor])),
    providePrimeNG({ theme: { preset: TraceflowPreset, options: { darkModeSelector: '.tf-dark' } }, ripple: false }),
    provideEchartsCore({ echarts: () => import('echarts') }),
    MessageService,
    ConfirmationService
  ]
};
