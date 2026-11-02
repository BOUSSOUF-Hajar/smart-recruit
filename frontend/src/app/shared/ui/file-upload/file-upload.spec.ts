import { TestBed } from '@angular/core/testing';
import { FileUpload, validateFile } from './file-upload';

const MB = 1024 * 1024;

function aFile(name: string, type: string, size: number): File {
  return new File([new Uint8Array(size)], name, { type });
}

describe('validateFile', () => {
  it('accepte un PDF de 5 Mo', () => {
    expect(
      validateFile(aFile('cv.pdf', 'application/pdf', 5 * MB), 'application/pdf', '.pdf', 5 * MB),
    ).toBeNull();
  });

  it('refuse un autre type ou une autre extension', () => {
    expect(
      validateFile(aFile('cv.docx', 'application/msword', 10), 'application/pdf', '.pdf', 5 * MB),
    ).toContain('PDF');
    expect(
      validateFile(aFile('photo.png', 'application/pdf', 10), 'application/pdf', '.pdf', 5 * MB),
    ).toContain('PDF');
  });

  it('refuse un fichier de plus de 5 Mo avant tout envoi', () => {
    expect(
      validateFile(
        aFile('cv.pdf', 'application/pdf', 5 * MB + 1),
        'application/pdf',
        '.pdf',
        5 * MB,
      ),
    ).toContain('5 Mo');
  });

  it('accepte un type vide si l’extension est bonne (certains navigateurs)', () => {
    expect(validateFile(aFile('CV.PDF', '', 10), 'application/pdf', '.pdf', 5 * MB)).toBeNull();
  });
});

describe('FileUpload', () => {
  it("n'émet que les fichiers acceptables et affiche la raison du refus", () => {
    const fixture = TestBed.createComponent(FileUpload);
    fixture.detectChanges();
    const emitted: File[] = [];
    fixture.componentInstance.fileSelected.subscribe((file) => emitted.push(file));

    fixture.componentInstance.check(aFile('cv.txt', 'text/plain', 10));
    fixture.detectChanges();
    fixture.componentInstance.check(aFile('cv.pdf', 'application/pdf', 10));

    expect(emitted.map((file) => file.name)).toEqual(['cv.pdf']);
  });
});
