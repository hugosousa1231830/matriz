import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'
import { isRetryable } from './api/errors'

const queryClient = new QueryClient({
  defaultOptions: {
    // Client errors (4xx) are final; network and server errors are retried twice.
    queries: { retry: (failures, error) => isRetryable(error) && failures < 2 },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
