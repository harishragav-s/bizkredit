import React, { createContext, useContext, useState, useCallback } from 'react';
import authService from '../services/authService';


const AuthContext = createContext(null);

export function AuthContextProvider({ children }) {

  const [user, setUser] = useState(() => {
    const stored = sessionStorage.getItem('bizkredit_user');
    return stored ? JSON.parse(stored) : null;
  });



  const login = useCallback(async (email, password) => {
    const response = await authService.login({ email, password });
    const authData = response.data.data;
    const loggedInUser = {
      userId: authData.userId,
      name: authData.name,
      email: authData.email,
      role: authData.role,
    };
    sessionStorage.setItem('bizkredit_token', authData.token);
    sessionStorage.setItem('bizkredit_user', JSON.stringify(loggedInUser));
    setUser(loggedInUser);
    return loggedInUser;
  }, []);

  const logout = useCallback(() => {
    if (user) {
      authService.logout(user.userId).catch(() => {});
    }
    sessionStorage.removeItem('bizkredit_token');
    sessionStorage.removeItem('bizkredit_user');
    setUser(null);
  }, [user]);

const isAuthenticated = !!user;

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be invoked within the scope of an AuthContextProvider');
  }
  return context;
}