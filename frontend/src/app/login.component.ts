import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { AuthService } from './auth.service';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  username = '';
  password = '';
  submitting = signal(false);

  constructor(public auth: AuthService, private http: HttpClient) {}

  submit(): void {
    if (!this.username || !this.password) return;
    this.submitting.set(true);
    this.auth.setMessage(null);

    const candidate = 'Basic ' + btoa(`${this.username}:${this.password}`);
    this.http
      .get(`${environment.apiUrl}/api/manga`, {
        headers: { Authorization: candidate }
      })
      .subscribe({
        next: () => {
          this.auth.setCredentials(this.username, this.password);
          this.submitting.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          if (err.status === 401) {
            this.auth.setMessage('Invalid username or password.');
          } else if (err.status === 0) {
            this.auth.setMessage('Cannot reach the server. Is the backend running?');
          } else {
            this.auth.setMessage(`Login failed (status ${err.status}).`);
          }
        }
      });
  }
}
