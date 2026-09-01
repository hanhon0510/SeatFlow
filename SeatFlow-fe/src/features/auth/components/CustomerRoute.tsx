import { Spin } from 'antd'
import { Navigate, Outlet } from 'react-router-dom'
import { ROUTES } from '../../../shared/constants/routes'
import { useAuth } from '../context/useAuth'

/**
 * Guards the browse-hold-pay flow. Buying is a customer action, so an admin is sent back to
 * their dashboard. Signed-out visitors are left alone: the catalogue is public.
 */
export function CustomerRoute() {
  const { isAuthenticated, isLoadingUser, isRestoring, user } = useAuth()

  if (isRestoring || (isAuthenticated && isLoadingUser)) {
    return (
      <section className="page-surface auth-loading">
        <Spin />
      </section>
    )
  }

  if (user?.role === 'ADMIN') {
    return <Navigate to={ROUTES.home} replace />
  }

  return <Outlet />
}
