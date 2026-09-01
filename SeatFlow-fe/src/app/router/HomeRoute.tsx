import { AdminDashboardPage } from '../../features/admin/dashboard/pages/AdminDashboardPage'
import { useAuth } from '../../features/auth/context/useAuth'
import { HealthPage } from '../../features/health/pages/HealthPage'

/** The landing page is the admin's dashboard, and the service health page for everyone else. */
export function HomeRoute() {
  const { user } = useAuth()

  return user?.role === 'ADMIN' ? <AdminDashboardPage /> : <HealthPage />
}
