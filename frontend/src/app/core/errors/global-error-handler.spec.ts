import { TestBed } from '@angular/core/testing';
import { GlobalErrorHandler } from './global-error-handler';
import { NotificationService } from './notification.service';
import { ApiError } from './api-error';

describe('GlobalErrorHandler', () => {
  const notifications = { error: vi.fn(), success: vi.fn() };
  let handler: GlobalErrorHandler;

  beforeEach(() => {
    notifications.error.mockReset();
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    TestBed.configureTestingModule({
      providers: [GlobalErrorHandler, { provide: NotificationService, useValue: notifications }],
    });
    handler = TestBed.inject(GlobalErrorHandler);
  });

  it('notifie une erreur JavaScript imprévue', () => {
    handler.handleError(new TypeError('undefined is not a function'));

    expect(notifications.error).toHaveBeenCalledTimes(1);
  });

  it("ne notifie pas deux fois une erreur HTTP déjà traitée par l'intercepteur", () => {
    const apiError: ApiError = {
      status: 500,
      code: 'INTERNAL_ERROR',
      title: 'Erreur',
      errors: [],
      extensions: {},
    };

    handler.handleError(apiError);

    expect(notifications.error).not.toHaveBeenCalled();
  });
});
