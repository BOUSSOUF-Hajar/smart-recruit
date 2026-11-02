import { applicationParams } from './recruiter-applications.models';

describe('applicationParams', () => {
  it("n'envoie que les filtres renseignés, jamais le nom de compétence affiché", () => {
    expect(
      applicationParams({
        status: 'SCREENING,SHORTLISTED',
        skillId: 's-1',
        skillName: 'Java',
        candidateName: '  idrissi ',
        submittedFrom: '',
      }),
    ).toEqual({ status: 'SCREENING,SHORTLISTED', skillId: 's-1', candidateName: 'idrissi' });
  });
});
