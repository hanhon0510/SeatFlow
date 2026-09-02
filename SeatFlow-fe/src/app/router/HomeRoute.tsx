import { Spin } from 'antd'
import { Navigate } from 'react-router-dom'
import { AdminDashboardPage } from '../../features/admin/dashboard/pages/AdminDashboardPage'
import { useAuth } from '../../features/auth/context/useAuth'
import { ROUTES } from '../../shared/constants/routes'

/** The landing page is the admin's dashboard, and the event catalogue for everyone else. */
export function HomeRoute() {
  const { isAuthenticated, isLoadingUser, isRestoring, user } = useAuth()

  // Redirecting before the stored session is known would bounce an admin through the
  // catalogue, which only sends them back here.
  if (isRestoring || (isAuthenticated && isLoadingUser)) {
    return (
      <section className="page-surface auth-loading">
        <Spin />
      </section>
    )
  }

  return user?.role === 'ADMIN' ? <AdminDashboardPage /> : <Navigate to={ROUTES.events} replace />
}
