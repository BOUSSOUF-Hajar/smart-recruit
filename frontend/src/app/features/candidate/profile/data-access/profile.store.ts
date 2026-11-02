import { httpResource } from '@angular/common/http';
import { Injectable, computed, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CandidateProfileApi } from './profile.api';
import {
  CandidateProfile,
  ProfileLanguage,
  ProfileLink,
  SkillEntryRequest,
  UpdateProfileRequest,
} from './profile.models';

/**
 * État de l'écran profil, fourni au niveau de la page. Le serveur reste la source de vérité :
 * chaque écriture renvoie le profil complet, qui remplace la valeur affichée (pas de mise à jour
 * optimiste, la version du serveur fait foi).
 */
@Injectable()
export class ProfileStore {
  private readonly api = inject(CandidateProfileApi);

  readonly resource = httpResource<CandidateProfile>(() => '/api/v1/candidates/me/profile');
  readonly profile = computed(() => (this.resource.hasValue() ? this.resource.value() : undefined));
  readonly completeness = computed(() => this.profile()?.completeness);

  reload(): void {
    this.resource.reload();
  }

  /** La version envoyée est celle de la dernière lecture : une modification faite ailleurs donne 409. */
  saveBasics(request: Omit<UpdateProfileRequest, 'version'>): Observable<CandidateProfile> {
    const version = this.profile()?.version ?? 0;
    return this.api
      .updateBasics({ ...request, version })
      .pipe(tap((profile) => this.apply(profile)));
  }

  saveSkills(skills: SkillEntryRequest[]): Observable<CandidateProfile> {
    return this.api.replaceSkills(skills).pipe(tap((profile) => this.apply(profile)));
  }

  saveLanguages(languages: ProfileLanguage[]): Observable<CandidateProfile> {
    return this.api.replaceLanguages(languages).pipe(tap((profile) => this.apply(profile)));
  }

  saveLinks(links: ProfileLink[]): Observable<CandidateProfile> {
    return this.api.replaceLinks(links).pipe(tap((profile) => this.apply(profile)));
  }

  private apply(profile: CandidateProfile): void {
    this.resource.set(profile);
  }
}
