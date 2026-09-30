import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '@/api/auth';
import { tokenStore } from '@/api/axiosClient';
import type { CurrentUser } from '@/types/domain';

interface AuthState {
  user: CurrentUser | null;
  initialising: boolean;
  signIn: (identifier: string, password: string, rememberMe: boolean) => Promise<CurrentUser>;
  signOut: () => Promise<void>;
  refreshUser: () => Promise<void>;
  can: (permission: string) => boolean;
  canAny: (permissions: string[]) => boolean;
  hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [initialising, setInitialising] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function restore() {
      if (!tokenStore.access()) {
        setInitialising(false);
        return;
      }
      try {
        const me = await authApi.me();
        if (!cancelled) {
          setUser(me);
        }
      } catch {
        tokenStore.clear();
      } finally {
        if (!cancelled) {
          setInitialising(false);
        }
      }
    }
    void restore();
    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback(async (identifier: string, password: string, rememberMe: boolean) => {
    const tokens = await authApi.login(identifier, password, rememberMe);
    tokenStore.set(tokens.accessToken, tokens.refreshToken);
    setUser(tokens.user);
    return tokens.user;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authApi.logout(tokenStore.refresh());
    } catch {
      // Signing out locally matters more than the server acknowledging it.
    } finally {
      tokenStore.clear();
      setUser(null);
    }
  }, []);

  const refreshUser = useCallback(async () => {
    setUser(await authApi.me());
  }, []);

  const value = useMemo<AuthState>(() => {
    const permissions = new Set(user?.permissions ?? []);
    return {
      user,
      initialising,
      signIn,
      signOut,
      refreshUser,
      can: (permission) => permissions.has(permission),
      canAny: (required) => required.length === 0 || required.some((p) => permissions.has(p)),
      hasRole: (role) => (user?.roles ?? []).includes(role),
    };
  }, [user, initialising, signIn, signOut, refreshUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
