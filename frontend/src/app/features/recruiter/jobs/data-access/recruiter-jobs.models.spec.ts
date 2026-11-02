import { JobStatus } from '../../../../shared/models/job';
import { availableActions, isEditable, totalApplications } from './recruiter-jobs.models';

describe('cycle de vie des offres (affichage)', () => {
  it.each<[JobStatus, string[]]>([
    ['DRAFT', ['publish', 'delete']],
    ['PUBLISHED', ['close', 'unpublish']],
    ['CLOSED', ['reopen', 'archive']],
    ['ARCHIVED', []],
  ])('propose les actions autorisées pour %s', (status, actions) => {
    expect(availableActions(status)).toEqual(actions);
  });

  it('RG-17 : seules les offres en brouillon ou publiées sont modifiables', () => {
    expect(isEditable('DRAFT')).toBe(true);
    expect(isEditable('PUBLISHED')).toBe(true);
    expect(isEditable('CLOSED')).toBe(false);
    expect(isEditable('ARCHIVED')).toBe(false);
  });

  it('additionne les candidatures de tous les statuts', () => {
    expect(totalApplications({})).toBe(0);
    expect(totalApplications({ SUBMITTED: 3, IN_REVIEW: 2, REJECTED: 1 })).toBe(6);
  });
});
