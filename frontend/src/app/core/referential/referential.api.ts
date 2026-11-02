import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SkillOption, SkillSearch } from '../../shared/ui/skill-autocomplete/skill-search';

export interface Category {
  id: string;
  name: string;
}

/** Référentiels communs (docs/api.md §3) : compétences et catégories d'offres. */
@Injectable({ providedIn: 'root' })
export class ReferentialApi implements SkillSearch {
  private readonly http = inject(HttpClient);

  search(query: string): Observable<SkillOption[]> {
    return this.http.get<SkillOption[]>('/api/v1/skills', { params: { q: query, size: 20 } });
  }

  create(name: string): Observable<SkillOption> {
    return this.http.post<SkillOption>('/api/v1/skills', { name });
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/v1/categories');
  }
}
