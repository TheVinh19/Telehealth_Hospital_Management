import React, { createContext, useContext, useState, useEffect } from 'react';
import { User, Role } from '../types';
import { api } from '../services/api';

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: (email: string, pass: string) => Promise<void>;
  register: (data: { email: string; password: string; fullName: string; phone?: string; role?: string }) => Promise<void>;
  switchDemoUser: (role: 'ADMIN' | 'DOCTOR' | 'DOCTOR_2' | 'PATIENT_1' | 'PATIENT_2') => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const token = api.getToken();
    if (token) {
      api.getMe()
        .then(u => setUser(u))
        .catch(() => {
          api.setToken(null);
          setUser(null);
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (email: string, pass: string) => {
    setLoading(true);
    try {
      const res = await api.login(email, pass);
      setUser(res.user);
    } finally {
      setLoading(false);
    }
  };

  const register = async (data: { email: string; password: string; fullName: string; phone?: string; role?: string }) => {
    setLoading(true);
    try {
      const res = await api.register(data);
      setUser(res.user);
    } finally {
      setLoading(false);
    }
  };

  const switchDemoUser = async (demoKey: 'ADMIN' | 'DOCTOR' | 'DOCTOR_2' | 'PATIENT_1' | 'PATIENT_2') => {
    let email = '';
    let pass = '';
    if (demoKey === 'ADMIN') {
      email = 'admin@demo.com';
      pass = 'admin123';
    } else if (demoKey === 'DOCTOR') {
      email = 'doctor.anh@demo.com';
      pass = 'doctor123';
    } else if (demoKey === 'DOCTOR_2') {
      email = 'doctor.binh@demo.com';
      pass = 'doctor123';
    } else if (demoKey === 'PATIENT_1') {
      email = 'patient1@demo.com';
      pass = 'patient123';
    } else if (demoKey === 'PATIENT_2') {
      email = 'patient2@demo.com';
      pass = 'patient123';
    }
    await login(email, pass);
  };

  const logout = () => {
    api.setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, switchDemoUser, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
