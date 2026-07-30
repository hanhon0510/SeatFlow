import { Result, Spin } from 'antd'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { ROUTES } from '../../../shared/constants/routes'
import { useAuth } from '../context/useAuth'

export function AdminRoute() {
  const location = useLocation()
  const { isAuthenticated, isLoadingUser, isRestoring, user } = useAuth()

  if (isRestoring || (isAuthenticated && isLoadingUser)) {
    return (
      <section className="page-surface auth-loading">
        <Spin />
      </section>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to={ROUTES.login} state={{ from: location }} replace />
  }

  if (user?.role !== 'ADMIN') {
    return (
      <section className="page-surface">
        <Result
          status="403"
          title="403"
          subTitle="You are not authorized to access this page."
        />
      </section>
    )
  }

  return <Outlet />
}
