import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@/app/queryClient';
import { AuthProvider } from '@/contexts/AuthContext';
import { ColorModeProvider } from '@/contexts/ColorModeContext';
import { AppRoutes } from '@/routes/AppRoutes';

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ColorModeProvider>
        <BrowserRouter>
          <AuthProvider>
            <AppRoutes />
          </AuthProvider>
        </BrowserRouter>
      </ColorModeProvider>
    </QueryClientProvider>
  );
}
