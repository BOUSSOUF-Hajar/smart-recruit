import { auditParams, startOfLocalDay } from './audit-params';

describe('auditParams', () => {
  it("couvre toute la journée de fin dans le fuseau de l'admin", () => {
    const params = auditParams({ from: '2026-10-01', to: '2026-10-03', result: 'FAILURE' });

    expect(params['from']).toBe(new Date(2026, 9, 1).toISOString());
    expect(params['to']).toBe(new Date(2026, 9, 4).toISOString());
    expect(params['result']).toBe('FAILURE');
    expect(params['action']).toBeUndefined();
  });

  it('passe au mois suivant quand il le faut', () => {
    expect(startOfLocalDay('2026-10-31', 1)).toBe(new Date(2026, 10, 1).toISOString());
  });
});
