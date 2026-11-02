import { Page, test as base, expect } from '@playwright/test';

/**
 * `test` de Playwright, avec une vérification automatique : une violation de la politique CSP ou
 * une erreur JavaScript non gérée fait échouer le test, même si l'écran a l'air de fonctionner.
 */
export const test = base.extend<{ browserErrors: void }>({
  browserErrors: [
    async ({ page }, use) => {
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(`Erreur JavaScript : ${error.message}`));
      page.on('console', (message) => {
        if (
          message.type() === 'error' &&
          /Content Security Policy|Refused to/i.test(message.text())
        ) {
          errors.push(`CSP : ${message.text()}`);
        }
      });
      await use();
      expect(errors, errors.join('\n')).toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };

/** Mot de passe commun des comptes de démonstration (profil demo, voir le README). */
export const DEMO_PASSWORD = process.env['DEMO_PASSWORD'] ?? 'Demo-SmartRecruit-2026';

export const DEMO = {
  admin: 'admin@smartrecruit.example',
  recruiter: 'recruteur@atlasia.example',
  candidate: 'candidat@demo.example',
};

export async function login(page: Page, email: string, password = DEMO_PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

/**
 * PDF d'une page avec quelques lignes de texte, construit à la volée : aucun binaire dans le dépôt.
 * Les décalages de la table xref sont calculés pour que PDFBox l'accepte comme un vrai PDF.
 */
export function pdfWithText(lines: string[]): Buffer {
  const text = lines
    .map(
      (line, index) =>
        `BT /F1 11 Tf 50 ${750 - index * 16} Td (${line.replace(/[()\\]/g, '')}) Tj ET`,
    )
    .join('\n');
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${Buffer.byteLength(text, 'latin1')} >>\nstream\n${text}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = '%PDF-1.4\n';
  const offsets: number[] = [];
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(body, 'latin1'));
    body += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = Buffer.byteLength(body, 'latin1');
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  body += offsets.map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`).join('');
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body, 'latin1');
}
