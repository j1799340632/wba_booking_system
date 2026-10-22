import './index.css'
import { StrictMode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom';
import router from '@/config/router/router.jsx';

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: 1,
            // The window does not automatically reacquire data when it gains focus
            refetchOnWindowFocus: false,
            // Data is considered "new" within 5 minutes.
            staleTime: 1000 * 60 * 5,
        },
    },
});

createRoot(document.getElementById('root')).render(
  <StrictMode>
      <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
      </QueryClientProvider>
  </StrictMode>,
)
