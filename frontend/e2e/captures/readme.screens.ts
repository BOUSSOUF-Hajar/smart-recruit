import { Page, test } from '@playwright/test';
import { DEMO, login } from '../support';

/** Dossier où le README va chercher ses images. */
const OUTPUT = '../docs/screenshots';

async function capture(page: Page, name: string): Promise<void> {
  await page.waitForLoadState('networkidle');
  // Laisse finir les animations (graphiques, onglets) avant la photo
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${OUTPUT}/${name}.png` });
}

test('recruteur : dossier, liste et tableau de bord', async ({ page }) => {
  await login(page, DEMO.recruiter);

  await page.goto('/recruiter/applications?status=INTERVIEW');
  await page.locator('table a[href^="/recruiter/applications/"]').first().click();
  await page.waitForURL(/\/recruiter\/applications\/[^?]+$/);
  await capture(page, 'dossier-candidature');

  await page.goto('/recruiter/applications');
  await capture(page, 'candidatures');

  await page.goto('/recruiter');
  await capture(page, 'tableau-de-bord-recruteur');
});

test('candidat : ses candidatures', async ({ page }) => {
  await login(page, DEMO.candidate);
  await page.goto('/candidate/applications');
  await capture(page, 'espace-candidat');
});

// Pas de capture du journal d'audit : les données de démonstration n'y écrivent rien, il ne montrerait
// que les connexions de ce script.
test('administrateur : tableau de bord', async ({ page }) => {
  await login(page, DEMO.admin);
  await page.goto('/admin');
  await capture(page, 'administration');
});
