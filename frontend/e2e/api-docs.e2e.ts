import { expect, test } from './support';

/**
 * US-58 : la documentation de l'API passe par Nginx (même origine) et s'affiche sans
 * violation CSP, vérifiée automatiquement par `support.ts`.
 */
test("Swagger UI présente l'API de démonstration", async ({ page }) => {
  await page.goto('/api/docs/swagger-ui.html');

  await expect(page.getByRole('heading', { name: /SmartRecruit API/ })).toBeVisible();
  await expect(page.getByText('/api/v1/auth/login').first()).toBeVisible();
});
