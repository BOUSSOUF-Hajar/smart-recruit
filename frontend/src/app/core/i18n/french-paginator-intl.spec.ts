import { FrenchPaginatorIntl } from './french-paginator-intl';

describe('FrenchPaginatorIntl', () => {
  const intl = new FrenchPaginatorIntl();

  it('affiche la plage de la page courante en français', () => {
    expect(intl.getRangeLabel(0, 10, 42)).toBe('1 – 10 sur 42');
    expect(intl.getRangeLabel(1, 10, 42)).toBe('11 – 20 sur 42');
  });

  it('borne la dernière page, souvent incomplète', () => {
    expect(intl.getRangeLabel(4, 10, 42)).toBe('41 – 42 sur 42');
  });

  it('gère une liste vide', () => {
    expect(intl.getRangeLabel(0, 10, 0)).toBe('0 sur 0');
  });

  it('traduit les libellés des boutons', () => {
    expect(intl.itemsPerPageLabel).toBe('Éléments par page');
    expect(intl.nextPageLabel).toBe('Page suivante');
  });
});
