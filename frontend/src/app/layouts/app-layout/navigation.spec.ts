import { navigationFor } from './navigation';

describe('navigationFor', () => {
  const links = (role: Parameters<typeof navigationFor>[0]) =>
    navigationFor(role).map((item) => item.link);

  it('donne à chaque rôle les entrées de son espace', () => {
    expect(links('CANDIDATE')).toEqual([
      '/candidate',
      '/candidate/profile',
      '/candidate/applications',
      '/candidate/interviews',
      '/jobs',
    ]);
    expect(links('RECRUITER')).toEqual([
      '/recruiter',
      '/recruiter/jobs',
      '/recruiter/applications',
      '/recruiter/interviews',
    ]);
    expect(links('ADMIN')).toEqual([
      '/admin',
      '/admin/users',
      '/admin/companies',
      '/admin/categories',
      '/admin/skills',
      '/admin/audit',
    ]);
    expect(links(null)).toEqual([]);
  });
});
