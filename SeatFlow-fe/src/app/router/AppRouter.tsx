import { Route, Routes } from 'react-router-dom'
import { AppShell } from '../layout/AppShell'
import { ProtectedRoute } from '../../features/auth/components/ProtectedRoute'
import { LoginPage } from '../../features/auth/pages/LoginPage'
import { RegisterPage } from '../../features/auth/pages/RegisterPage'
import { HealthPage } from '../../features/health/pages/HealthPage'
import { NotFoundPage } from '../../features/not-found/pages/NotFoundPage'
import { PlaceholderPage } from '../../features/placeholder/pages/PlaceholderPage'
import { ROUTES } from '../../shared/constants/routes'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HealthPage />} />
        <Route path={ROUTES.login} element={<LoginPage />} />
        <Route path={ROUTES.register} element={<RegisterPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path={ROUTES.events} element={<PlaceholderPage title="Events" />} />
          <Route path={ROUTES.admin} element={<PlaceholderPage title="Admin" />} />
        </Route>
        <Route path={ROUTES.notFound} element={<NotFoundPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
