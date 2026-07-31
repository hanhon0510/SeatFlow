import { Route, Routes } from 'react-router-dom'
import { AppShell } from '../layout/AppShell'
import { AdminRoute } from '../../features/auth/components/AdminRoute'
import { ProtectedRoute } from '../../features/auth/components/ProtectedRoute'
import { LoginPage } from '../../features/auth/pages/LoginPage'
import { RegisterPage } from '../../features/auth/pages/RegisterPage'
import { VenueFormPage } from '../../features/admin/venues/pages/VenueFormPage'
import { VenueListPage } from '../../features/admin/venues/pages/VenueListPage'
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
          <Route element={<AdminRoute />}>
            <Route path={ROUTES.admin} element={<VenueListPage />} />
            <Route
              path={ROUTES.adminVenueNew}
              element={<VenueFormPage mode="create" />}
            />
            <Route
              path={ROUTES.adminVenueEditPattern}
              element={<VenueFormPage mode="edit" />}
            />
          </Route>
        </Route>
        <Route path={ROUTES.notFound} element={<NotFoundPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
