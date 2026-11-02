import { DEMO, expect, login, test } from './support';

/**
 * Parcours critique n° 2 : le recruteur de démonstration ouvre une candidature reçue, la fait passer
 * en présélection et retrouve le changement dans l'historique.
 */
test('connexion recruteur puis changement de statut', async ({ page }) => {
  await login(page, DEMO.recruiter);
  await expect(page).toHaveURL(/\/recruiter/);

  await page.goto('/recruiter/applications?status=SUBMITTED');
  const firstApplication = page.locator('table a[href^="/recruiter/applications/"]').first();
  const candidate = (await firstApplication.textContent())?.trim() ?? '';
  await firstApplication.click();
  await expect(page.getByRole('heading', { name: candidate })).toBeVisible();

  await page.getByRole('button', { name: 'Faire avancer' }).click();
  await page.getByRole('radio', { name: 'Passer en présélection' }).check();
  await page.getByRole('button', { name: 'Valider' }).click();
  await expect(page.locator('app-application-status').first()).toContainText('Présélection');

  await page.getByRole('tab', { name: 'Historique' }).click();
  await expect(page.getByText('Présélection').first()).toBeVisible();
});
