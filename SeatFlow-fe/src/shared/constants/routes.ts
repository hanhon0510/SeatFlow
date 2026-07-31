export const ROUTES = {
  home: '/',
  login: '/login',
  register: '/register',
  events: '/events',
  admin: '/admin',
  adminVenueNew: '/admin/venues/new',
  adminVenueEditPattern: '/admin/venues/:venueId/edit',
  adminVenueEdit: (venueId: string) => `/admin/venues/${venueId}/edit`,
  notFound: '/not-found',
} as const
