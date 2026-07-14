import { Navigate, Route, Routes } from 'react-router-dom'
import { HomePage } from '../../features/home/pages/HomePage'
import { ROUTES } from '../../shared/constants/routes'

export function AppRouter() {
  return (
    <Routes>
      <Route path={ROUTES.home} element={<HomePage />} />
      <Route path="*" element={<Navigate to={ROUTES.home} replace />} />
    </Routes>
  )
}
