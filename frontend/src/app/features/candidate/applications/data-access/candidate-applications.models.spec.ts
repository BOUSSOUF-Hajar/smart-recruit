import { profileGaps } from './candidate-applications.models';

describe('profileGaps', () => {
  it("traduit les manques en liens vers l'onglet du profil", () => {
    expect(profileGaps(['headline', 'resume'])).toEqual([
      { key: 'headline', label: 'Titre professionnel', tab: 0 },
      { key: 'resume', label: 'Un CV', tab: 4 },
    ]);
  });

  it('ignore les codes inconnus et une réponse mal formée', () => {
    expect(profileGaps(['skills', 'avatar', 42])).toHaveLength(1);
    expect(profileGaps(undefined)).toEqual([]);
  });
});
