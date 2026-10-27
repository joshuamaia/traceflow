import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { catchError, finalize, throwError } from 'rxjs';
import { AppState } from '../state/app-state';
import { ApiError } from './models';

/** Set to true on a request to handle its errors locally instead of showing a toast. */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

/** Tracks in-flight requests for the header processing indicator. */
export const activityInterceptor: HttpInterceptorFn = (req, next) => {
  const state = inject(AppState);
  state.requestStarted();
  return next(req).pipe(finalize(() => state.requestFinished()));
};

/** Shows a toast for failed requests using the server provided (sanitized) message. */
export const errorToastInterceptor: HttpInterceptorFn = (req, next) => {
  const messages = inject(MessageService);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !req.context.get(SILENT_ERRORS)) {
        messages.add({ severity: 'error', summary: errorTitle(error), detail: errorMessage(error), life: 6000 });
      }
      return throwError(() => error);
    })
  );
};

export function errorTitle(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Backend unreachable';
  }
  return `Request failed (${error.status})`;
}

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Could not connect to the TraceFlow API. Check that the backend is running.';
    }
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && typeof body.message === 'string') {
      const violations = body.violations?.map((v) => `${v.field}: ${v.message}`).join('; ');
      return violations ? `${body.message} (${violations})` : body.message;
    }
    return error.statusText || 'Unexpected error';
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}
