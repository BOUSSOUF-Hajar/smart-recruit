import { formatSalary } from './job';

/** Les séparateurs de milliers de fr-FR sont des espaces insécables : on les normalise. */
const normalize = (text: string | null) => text?.replace(/\s/g, ' ') ?? null;

describe('formatSalary', () => {
  const base = { currency: 'MAD', period: 'MONTHLY' as const };

  it('affiche une fourchette avec la devise et la période', () => {
    expect(normalize(formatSalary({ ...base, min: 15000, max: 22000 }))).toBe(
      '15 000 – 22 000 MAD par mois',
    );
  });

  it('gère une seule borne', () => {
    expect(normalize(formatSalary({ ...base, min: 15000, max: null }))).toBe(
      'à partir de 15 000 MAD par mois',
    );
    expect(normalize(formatSalary({ ...base, min: null, max: 22000 }))).toBe(
      "jusqu'à 22 000 MAD par mois",
    );
  });

  it('ne renvoie rien quand le salaire est masqué ou absent', () => {
    expect(formatSalary(null)).toBeNull();
    expect(formatSalary({ ...base, min: null, max: null })).toBeNull();
  });
});
