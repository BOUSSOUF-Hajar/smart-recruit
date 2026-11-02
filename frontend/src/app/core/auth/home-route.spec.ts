import { homeFor, safeReturnUrl } from './home-route';

describe('homeFor', () => {
  it('donne le tableau de bord de chaque rôle', () => {
    expect(homeFor('CANDIDATE')).toBe('/candidate');
    expect(homeFor('RECRUITER')).toBe('/recruiter');
    expect(homeFor('ADMIN')).toBe('/admin');
    expect(homeFor(null)).toBe('/jobs');
  });
});

describe('safeReturnUrl', () => {
  it('accepte un chemin interne', () => {
    expect(safeReturnUrl('/candidate/applications?page=2')).toBe('/candidate/applications?page=2');
  });

  it.each([
    'https://pirate.example',
    '//pirate.example',
    '/\\pirate.example',
    'candidate',
    '',
    null,
  ])('refuse %s, qui permettrait une redirection vers un autre site', (value) => {
    expect(safeReturnUrl(value)).toBeNull();
  });
});
