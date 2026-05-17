import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../environments/environment';

export interface Manga {
  id: number;
  title: string;
  coverUrl: string | null;
  latestChapter: string | null;
  nextCheckDate: string | null;
}

@Injectable({ providedIn: 'root' })
export class MangaService {
  private apiUrl = `${environment.apiUrl}/api/manga`;

  constructor(private http: HttpClient) {}

  list(): Observable<Manga[]> {
    return this.http.get<Manga[]>(this.apiUrl);
  }

  add(title: string): Observable<Manga> {
    return this.http.post<Manga>(this.apiUrl, { title });
  }

  check(id: number): Observable<Manga> {
    return this.http.post<Manga>(`${this.apiUrl}/${id}/check`, null);
  }

  remove(id: number): Observable<void> {
    return this.http.delete(`${this.apiUrl}/${id}`, { observe: 'response' }).pipe(
      map(() => void 0)
    );
  }
}
