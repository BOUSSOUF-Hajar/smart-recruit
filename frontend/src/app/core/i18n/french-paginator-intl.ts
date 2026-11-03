import { Injectable, Provider } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';

/**
 * Libellés français des paginateurs Angular Material, qui n'existent qu'en anglais
 * (« 1 – 10 of 42 »). Fournie par les écrans chargés à la demande ({@link FRENCH_PAGINATOR}) et non
 * dans app.config : l'importer au démarrage ajouterait tout le paginateur au bundle initial (+185 ko).
 */
@Injectable()
export class FrenchPaginatorIntl extends MatPaginatorIntl {
  override itemsPerPageLabel = $localize`Éléments par page`;
  override nextPageLabel = $localize`Page suivante`;
  override previousPageLabel = $localize`Page précédente`;
  override firstPageLabel = $localize`Première page`;
  override lastPageLabel = $localize`Dernière page`;

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return $localize`0 sur ${length}:total:`;
    }
    const start = page * pageSize + 1;
    // La dernière page peut être incomplète ; une page au-delà de la fin n'affiche que le total
    const end = Math.min((page + 1) * pageSize, length);
    return start > length
      ? $localize`${length}:total: au total`
      : $localize`${start}:start: – ${end}:end: sur ${length}:total:`;
  };
}

/** À placer dans les `providers` d'un composant : vaut pour tous les paginateurs affichés sous lui. */
export const FRENCH_PAGINATOR: Provider = {
  provide: MatPaginatorIntl,
  useClass: FrenchPaginatorIntl,
};
