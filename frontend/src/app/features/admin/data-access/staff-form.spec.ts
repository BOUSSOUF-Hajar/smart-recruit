import { toStaffRequest } from './staff-form';

describe('toStaffRequest', () => {
  const value = {
    email: '  Youssef.Alami@Entreprise.MA ',
    firstName: ' Youssef ',
    lastName: 'Alami ',
    role: 'RECRUITER' as const,
    companyId: 'c-1',
    jobTitle: '  ',
  };

  it("nettoie les champs d'un recruteur et garde son entreprise", () => {
    expect(toStaffRequest(value)).toEqual({
      email: 'youssef.alami@entreprise.ma',
      firstName: 'Youssef',
      lastName: 'Alami',
      role: 'RECRUITER',
      companyId: 'c-1',
      jobTitle: null,
    });
  });

  it("retire l'entreprise et l'intitulé d'un administrateur", () => {
    const request = toStaffRequest({ ...value, role: 'ADMIN', jobTitle: 'DRH' });

    expect(request.companyId).toBeNull();
    expect(request.jobTitle).toBeNull();
  });
});
