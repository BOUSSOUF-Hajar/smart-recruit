import { DEMO, expect, login, test } from './support';

/** Parcours critique n° 3 : un rôle n'entre pas dans l'espace d'un autre, ni par l'écran ni par l'API. */
test("un candidat est renvoyé vers son espace et l'API lui refuse l'administration", async ({
  page,
}) => {
  await login(page, DEMO.candidate);
  await expect(page).toHaveURL(/\/candidate/);

  await page.goto('/recruiter/applications');
  await expect(page).toHaveURL(/\/candidate$/);
  await page.goto('/admin/users');
  await expect(page).toHaveURL(/\/candidate$/);

  // L'écran n'est qu'un confort : l'API est la vraie barrière
  const token = await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'include',
    });
    return ((await response.json()) as { accessToken: string }).accessToken;
  });
  const response = await page.request.get('/api/v1/admin/users', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.status()).toBe(403);
});

test('un visiteur non connecté est renvoyé vers la connexion', async ({ page }) => {
  await page.goto('/recruiter/applications');
  await expect(page).toHaveURL(/\/login\?returnUrl=/);
});
