import { hasStarted, weekRange } from './interviews.models';

describe('agenda', () => {
  it('borne la semaine du lundi 0 h au lundi suivant, en heure locale', () => {
    const { from, to } = weekRange(new Date(2026, 9, 22, 15, 30)); // jeudi 22 octobre 2026

    expect(new Date(from)).toEqual(new Date(2026, 9, 19));
    expect(new Date(to)).toEqual(new Date(2026, 9, 26));
  });

  it('un dimanche appartient à la semaine commencée le lundi précédent', () => {
    expect(new Date(weekRange(new Date(2026, 9, 25, 9)).from)).toEqual(new Date(2026, 9, 19));
  });

  it('un entretien se clôture seulement une fois commencé', () => {
    const now = new Date('2026-10-22T10:00:00Z');
    expect(hasStarted({ startsAt: '2026-10-22T09:59:00Z' }, now)).toBe(true);
    expect(hasStarted({ startsAt: '2026-10-22T10:30:00Z' }, now)).toBe(false);
  });
});
