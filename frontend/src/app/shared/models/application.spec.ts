import {
  APPLICATION_STATUSES,
  APPLICATION_STATUS_LABELS,
  CANDIDATE_STATUS_LABELS,
  isFinalStatus,
} from './application';

describe('statuts de candidature', () => {
  it('RG-16 : seuls HIRED, REJECTED et WITHDRAWN sont définitifs', () => {
    expect(APPLICATION_STATUSES.filter(isFinalStatus)).toEqual(['HIRED', 'REJECTED', 'WITHDRAWN']);
  });

  it('chaque statut a un libellé, interne comme candidat', () => {
    expect(Object.keys(APPLICATION_STATUS_LABELS)).toHaveLength(8);
    expect(CANDIDATE_STATUS_LABELS.IN_REVIEW).toBe("En cours d'examen");
    expect(CANDIDATE_STATUS_LABELS.NOT_SELECTED).toBe('Non retenue');
  });
});
