import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CandidateProfile,
  Education,
  EducationRequest,
  Experience,
  ExperienceRequest,
  ProfileLanguage,
  ProfileLink,
  ResumeInfo,
  SkillEntryRequest,
  UpdateProfileRequest,
} from './profile.models';

const ME = '/api/v1/candidates/me';

/** Endpoints `/candidates/me/**` ; l'identité vient du token, jamais de l'URL. */
@Injectable({ providedIn: 'root' })
export class CandidateProfileApi {
  private readonly http = inject(HttpClient);

  updateBasics(request: UpdateProfileRequest): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${ME}/profile`, request);
  }

  addExperience(request: ExperienceRequest): Observable<Experience> {
    return this.http.post<Experience>(`${ME}/experiences`, request);
  }

  updateExperience(id: string, request: ExperienceRequest): Observable<Experience> {
    return this.http.put<Experience>(`${ME}/experiences/${id}`, request);
  }

  deleteExperience(id: string): Observable<void> {
    return this.http.delete<void>(`${ME}/experiences/${id}`);
  }

  addEducation(request: EducationRequest): Observable<Education> {
    return this.http.post<Education>(`${ME}/educations`, request);
  }

  updateEducation(id: string, request: EducationRequest): Observable<Education> {
    return this.http.put<Education>(`${ME}/educations/${id}`, request);
  }

  deleteEducation(id: string): Observable<void> {
    return this.http.delete<void>(`${ME}/educations/${id}`);
  }

  replaceSkills(skills: SkillEntryRequest[]): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${ME}/skills`, { skills });
  }

  replaceLanguages(languages: ProfileLanguage[]): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${ME}/languages`, { languages });
  }

  replaceLinks(links: ProfileLink[]): Observable<CandidateProfile> {
    return this.http.put<CandidateProfile>(`${ME}/links`, { links });
  }

  uploadResume(file: File): Observable<ResumeInfo> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ResumeInfo>(`${ME}/resume`, body);
  }

  /** Le fichier passe par HttpClient (et donc par le token) : un simple lien ne serait pas authentifié. */
  /** US-19 : effacé, ou seulement désactivé s'il est joint à une candidature (le serveur décide). */
  deleteResume(): Observable<void> {
    return this.http.delete<void>(`${ME}/resume`);
  }

  downloadResume(): Observable<Blob> {
    return this.http.get(`${ME}/resume/file`, { responseType: 'blob' });
  }
}
