import { HttpErrorResponse } from '@angular/common/http';

/** Erreur de champ renvoyée avec VALIDATION_FAILED. */
export interface FieldViolation {
  field: string;
  message: string;
}

/**
 * Erreur de l'API, au format Problem Details (RFC 9457) du contrat SmartRecruit.
 * Les écrans choisissent leur message à partir de `code`, jamais à partir du statut seul.
 */
export interface ApiError {
  /** Statut HTTP ; 0 si le serveur n'a pas pu être joint. */
  status: number;
  code: string;
  title: string;
  detail?: string;
  correlationId?: string;
  errors: FieldViolation[];
  /** Informations métier propres à l'erreur (ex. `missing` pour PROFILE_INCOMPLETE). */
  extensions: Record<string, unknown>;
}

/** Code utilisé quand le serveur est injoignable (aucune réponse HTTP). */
export const NETWORK_ERROR = 'NETWORK_ERROR';

const KNOWN_FIELDS = new Set([
  'type',
  'title',
  'status',
  'detail',
  'instance',
  'code',
  'correlationId',
  'errors',
]);

/** Transforme n'importe quelle erreur HTTP en ApiError, même si le corps n'est pas au format attendu. */
export function toApiError(response: HttpErrorResponse): ApiError {
  if (response.status === 0) {
    return {
      status: 0,
      code: NETWORK_ERROR,
      title: 'Serveur injoignable',
      errors: [],
      extensions: {},
    };
  }

  const body = isRecord(response.error) ? response.error : {};
  const extensions = Object.fromEntries(
    Object.entries(body).filter(([key]) => !KNOWN_FIELDS.has(key)),
  );

  return {
    status: response.status,
    code: typeof body['code'] === 'string' ? body['code'] : fallbackCode(response.status),
    title: typeof body['title'] === 'string' ? body['title'] : response.statusText,
    detail: typeof body['detail'] === 'string' ? body['detail'] : undefined,
    correlationId:
      typeof body['correlationId'] === 'string'
        ? body['correlationId']
        : (response.headers?.get('X-Correlation-Id') ?? undefined),
    errors: Array.isArray(body['errors']) ? (body['errors'] as FieldViolation[]) : [],
    extensions,
  };
}

export function isApiError(value: unknown): value is ApiError {
  return (
    isRecord(value) && typeof value['code'] === 'string' && typeof value['status'] === 'number'
  );
}

function fallbackCode(status: number): string {
  return status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN_ERROR';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
