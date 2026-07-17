import { Route, Routes } from 'react-router-dom'
import { AppShell } from '../layout/AppShell'
import { HealthPage } from '../../features/health/pages/HealthPage'
import { NotFoundPage } from '../../features/not-found/pages/NotFoundPage'
import { PlaceholderPage } from '../../features/placeholder/pages/PlaceholderPage'
import { ROUTES } from '../../shared/constants/routes'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HealthPage />} />
        <Route path={ROUTES.login} element={<PlaceholderPage title="Login" />} />
        <Route path={ROUTES.register} element={<PlaceholderPage title="Register" />} />
        <Route path={ROUTES.events} element={<PlaceholderPage title="Events" />} />
        <Route path={ROUTES.admin} element={<PlaceholderPage title="Admin" />} />
        <Route path={ROUTES.notFound} element={<NotFoundPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
