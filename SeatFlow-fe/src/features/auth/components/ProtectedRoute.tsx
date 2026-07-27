import { Spin } from 'antd'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { ROUTES } from '../../../shared/constants/routes'
import { useAuth } from '../context/useAuth'

export function ProtectedRoute() {
  const location = useLocation()
  const { isAuthenticated, isRestoring } = useAuth()

  if (isRestoring) {
    return (
      <section className="page-surface auth-loading">
        <Spin />
      </section>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to={ROUTES.login} state={{ from: location }} replace />
  }

  return <Outlet />
}
