import { Route, Routes } from 'react-router-dom'
import { AppShell } from '../layout/AppShell'
import { AdminRoute } from '../../features/auth/components/AdminRoute'
import { CustomerRoute } from '../../features/auth/components/CustomerRoute'
import { ProtectedRoute } from '../../features/auth/components/ProtectedRoute'
import { HomeRoute } from './HomeRoute'
import { LoginPage } from '../../features/auth/pages/LoginPage'
import { ForgotPasswordPage } from '../../features/auth/pages/ForgotPasswordPage'
import { RegisterPage } from '../../features/auth/pages/RegisterPage'
import { ResetPasswordPage } from '../../features/auth/pages/ResetPasswordPage'
import { VenueFormPage } from '../../features/admin/venues/pages/VenueFormPage'
import { VenueListPage } from '../../features/admin/venues/pages/VenueListPage'
import { PromoCodeGeneratorPage } from '../../features/admin/promo-codes/pages/PromoCodeGeneratorPage'
import { CheckInPage } from '../../features/admin/check-in/pages/CheckInPage'
import { AdminEventDetailPage } from '../../features/admin/events/pages/AdminEventDetailPage'
import { EventFormPage } from '../../features/admin/events/pages/EventFormPage'
import { EventListPage } from '../../features/admin/events/pages/EventListPage'
import { NotFoundPage } from '../../features/not-found/pages/NotFoundPage'
import { EventCatalogPage } from '../../features/events/pages/EventCatalogPage'
import { EventDetailPage } from '../../features/events/pages/EventDetailPage'
import { CheckoutPage } from '../../features/holds/pages/CheckoutPage'
import { PaymentResultPage } from '../../features/checkout/pages/PaymentResultPage'
import { TicketDetailPage } from '../../features/tickets/pages/TicketDetailPage'
import { TicketListPage } from '../../features/tickets/pages/TicketListPage'
import { ROUTES } from '../../shared/constants/routes'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomeRoute />} />
        <Route path={ROUTES.login} element={<LoginPage />} />
        <Route path={ROUTES.register} element={<RegisterPage />} />
        <Route path={ROUTES.forgotPassword} element={<ForgotPasswordPage />} />
        <Route path={ROUTES.resetPassword} element={<ResetPasswordPage />} />
        <Route element={<CustomerRoute />}>
          <Route path={ROUTES.events} element={<EventCatalogPage />} />
          <Route path={ROUTES.eventDetailPattern} element={<EventDetailPage />} />
        </Route>
        <Route element={<ProtectedRoute />}>
          <Route element={<CustomerRoute />}>
            <Route path={ROUTES.checkoutPattern} element={<CheckoutPage />} />
            <Route path={ROUTES.paymentResultPattern} element={<PaymentResultPage />} />
            <Route path={ROUTES.tickets} element={<TicketListPage />} />
            <Route path={ROUTES.ticketDetailPattern} element={<TicketDetailPage />} />
          </Route>
          <Route element={<AdminRoute />}>
            <Route path={ROUTES.admin} element={<VenueListPage />} />
            <Route path={ROUTES.adminEvents} element={<EventListPage />} />
			<Route path={ROUTES.adminPromoCodes} element={<PromoCodeGeneratorPage />} />
            <Route
              path={ROUTES.adminEventNew}
              element={<EventFormPage mode="create" />}
            />
            <Route
              path={ROUTES.adminEventDetailPattern}
              element={<AdminEventDetailPage />}
            />
            <Route path={ROUTES.adminEventCheckInPattern} element={<CheckInPage />} />
            <Route
              path={ROUTES.adminEventEditPattern}
              element={<EventFormPage mode="edit" />}
            />
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
