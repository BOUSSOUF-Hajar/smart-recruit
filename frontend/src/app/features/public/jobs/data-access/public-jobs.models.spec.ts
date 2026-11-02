import { searchParams } from './public-jobs.models';

describe('searchParams', () => {
  it("garde les filtres renseignés et retire les valeurs vides de l'URL", () => {
    expect(
      searchParams({ q: '  angular ', city: '', workMode: 'REMOTE', salaryMin: '   ', page: '2' }),
    ).toEqual({ q: 'angular', workMode: 'REMOTE', page: '2' });
  });

  it('renvoie un objet vide sans filtre', () => {
    expect(searchParams({})).toEqual({});
  });
});
