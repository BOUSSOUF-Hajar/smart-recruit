import { expect, pdfWithText, test } from './support';

/**
 * Parcours critique n° 1 : un visiteur crée son compte, complète ce qu'il faut pour postuler
 * (titre, une compétence, un CV) puis postule et retrouve sa candidature.
 */
test('inscription, profil minimal, CV puis candidature', async ({ page }) => {
  const email = `e2e-${Date.now()}@demo.example`;

  await page.goto('/register');
  await page.getByLabel('Prénom').fill('Inès');
  await page.getByLabel('Nom', { exact: true }).fill('Testeuse');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill('une phrase de passe assez longue');
  await page.getByText("J'accepte que mes données soient traitées").click();
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page).toHaveURL(/\/candidate/);

  // Profil : titre professionnel
  await page.goto('/candidate/profile');
  await page.getByLabel('Titre professionnel').fill('Développeuse Java');
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Informations enregistrées.')).toBeVisible();

  // Compétences : une compétence du référentiel
  await page.getByRole('tab', { name: 'Compétences et langues' }).click();
  await page.getByLabel('Ajouter une compétence').fill('java');
  await page.getByRole('option', { name: 'Java', exact: true }).click();
  await page.getByRole('button', { name: 'Enregistrer' }).first().click();
  await expect(page.getByText('Compétences enregistrées.')).toBeVisible();

  // CV
  await page.getByRole('tab', { name: 'CV' }).click();
  await page.locator('input[type="file"]').setInputFiles({
    name: 'cv-ines.pdf',
    mimeType: 'application/pdf',
    buffer: pdfWithText(['Ines Testeuse', 'Developpeuse Java', 'Competences : Java, Spring Boot']),
  });
  await expect(page.getByText('cv-ines.pdf')).toBeVisible();

  // Candidature à une offre publiée
  await page.goto('/jobs');
  await page.getByRole('link', { name: 'Ingénieur·e DevOps' }).first().click();
  await page.getByRole('button', { name: 'Postuler' }).click();
  await page.getByRole('button', { name: 'Confirmer ma candidature' }).click();
  await expect(page.getByText('Candidature envoyée.')).toBeVisible();

  await page.goto('/candidate/applications');
  const row = page.getByRole('listitem').filter({ hasText: 'Ingénieur·e DevOps' });
  await expect(row).toBeVisible();
  await expect(row).toContainText('Envoyée');
});
