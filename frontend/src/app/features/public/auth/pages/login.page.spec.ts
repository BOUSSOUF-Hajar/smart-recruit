import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { LoginPage } from './login.page';
import { AuthService } from '../../../../core/auth/auth.service';
import { ApiError } from '../../../../core/errors/api-error';
import { aUser } from '../../../../core/auth/test-data';
import { AuthResponse } from '../../../../core/auth/auth.models';

describe('LoginPage', () => {
  const login = vi.fn<() => Observable<AuthResponse>>();

  beforeEach(() => {
    login.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { login } }],
    });
  });

  function render(returnUrl?: string) {
    const fixture = TestBed.createComponent(LoginPage);
    if (returnUrl) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    const fill = (selector: string, value: string) => {
      const input = element.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = async () => {
      fill('input[formControlName=email]', 'salma@example.com');
      fill('input[formControlName=password]', 'une-phrase-de-passe-longue');
      element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await fixture.whenStable();
    };
    return { fixture, element, submit };
  }

  it('affiche un message unique si les identifiants sont refusés', async () => {
    const error: ApiError = {
      status: 401,
      code: 'AUTH_INVALID_CREDENTIALS',
      title: 'Identifiants invalides',
      errors: [],
      extensions: {},
    };
    login.mockReturnValue(throwError(() => error));
    const { element, submit } = render();

    await submit();

    expect(element.querySelector('[role=alert]')?.textContent).toContain(
      'Email ou mot de passe incorrect.',
    );
  });

  it('revient à la page demandée après la connexion', async () => {
    login.mockReturnValue(of({ accessToken: 't', expiresIn: 900, user: aUser() }));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { submit } = render('/candidate/applications');

    await submit();

    expect(navigate).toHaveBeenCalledWith('/candidate/applications');
  });

  it('ignore une adresse de retour externe et va au tableau de bord du rôle', async () => {
    login.mockReturnValue(
      of({ accessToken: 't', expiresIn: 900, user: aUser({ role: 'RECRUITER' }) }),
    );
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { submit } = render('https://pirate.example');

    await submit();

    expect(navigate).toHaveBeenCalledWith('/recruiter');
  });

  it('impose le changement de mot de passe temporaire', async () => {
    login.mockReturnValue(
      of({ accessToken: 't', expiresIn: 900, user: aUser({ mustChangePassword: true }) }),
    );
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { submit } = render('/candidate');

    await submit();

    expect(navigate).toHaveBeenCalledWith('/account/password');
  });
});
