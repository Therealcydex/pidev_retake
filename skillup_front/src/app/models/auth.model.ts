import { Role } from './user.model';

export type { Role };

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  id: number;
  username: string;
  email: string;
  role: Role;
}

export interface UserInfo {
  id: number;
  username: string;
  email: string;
  role: string;
}
