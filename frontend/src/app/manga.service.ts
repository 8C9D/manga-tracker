import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../environments/environment';

export interface Manga {
  id: number;
  title: string;
  coverUrl: string | null;
  latestChapter: string | null;
  lastReadChapter: string | null;
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

  checkAll(): Observable<Manga[]> {
    return this.http.post<Manga[]>(`${this.apiUrl}/check-all`, null);
  }

  check(id: number): Observable<Manga> {
    return this.http.post<Manga>(`${this.apiUrl}/${id}/check`, null);
  }

  markRead(id: number, chapter: string): Observable<Manga> {
    return this.http.patch<Manga>(`${this.apiUrl}/${id}/read`, { chapter });
  }

  removeAll(): Observable<void> {
    return this.http.delete(this.apiUrl, { observe: 'response' }).pipe(
      map(() => void 0)
    );
  }

  remove(id: number): Observable<void> {
    return this.http.delete(`${this.apiUrl}/${id}`, { observe: 'response' }).pipe(
      map(() => void 0)
    );
  }
}
