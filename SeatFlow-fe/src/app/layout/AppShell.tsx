import { useState } from 'react'
import type { ReactNode } from 'react'
import {
  CalendarOutlined,
  DollarOutlined,
  HomeOutlined,
  LockOutlined,
  LoginOutlined,
  LogoutOutlined,
  MenuOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import { Button, Drawer, Layout, Menu, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { ROUTES } from '../../shared/constants/routes'
import { useAuth } from '../../features/auth/context/useAuth'

const { Header, Content, Footer } = Layout
const logoutKey = 'logout'

type NavItem = {
  key: string
  label: string
  icon: ReactNode
}

const publicNavItems = [
  { key: ROUTES.home, label: 'Health', icon: <HomeOutlined /> },
  { key: ROUTES.events, label: 'Events', icon: <CalendarOutlined /> },
] satisfies NavItem[]

const authenticatedNavItems = [] satisfies NavItem[]

const adminNavItems = [
  { key: ROUTES.admin, label: 'Admin', icon: <LockOutlined /> },
  { key: ROUTES.adminEvents, label: 'Event setup', icon: <DollarOutlined /> },
] satisfies NavItem[]

const guestNavItems = [
  { key: ROUTES.login, label: 'Login', icon: <LoginOutlined /> },
  { key: ROUTES.register, label: 'Register', icon: <UserAddOutlined /> },
] satisfies NavItem[]

const logoutNavItem = {
  key: logoutKey,
  label: 'Logout',
  icon: <LogoutOutlined />,
} satisfies NavItem

function buildMenuItems(items: NavItem[], closeDrawer?: () => void): MenuProps['items'] {
  return items.map((item) => ({
    key: item.key,
    icon: item.icon,
    label:
      item.key === logoutKey ? (
        item.label
      ) : (
        <NavLink to={item.key} onClick={closeDrawer}>
          {item.label}
        </NavLink>
      ),
  }))
}

function selectedKey(pathname: string, items: NavItem[]) {
  return items.some((item) => item.key === pathname) ? [pathname] : []
}

export function AppShell() {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const { isAuthenticated, logout, user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const closeDrawer = () => setDrawerOpen(false)
  const roleBasedNavItems = user?.role === 'ADMIN'
    ? [...authenticatedNavItems, ...adminNavItems]
    : authenticatedNavItems
  const visibleNavItems = isAuthenticated
    ? [...publicNavItems, ...roleBasedNavItems, logoutNavItem]
    : [...publicNavItems, ...guestNavItems]

  const handleLogout = async () => {
    closeDrawer()
    await logout()
    navigate(ROUTES.login, { replace: true })
  }

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === logoutKey) {
      void handleLogout()
    }
  }

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        <NavLink className="brand-link" to={ROUTES.home}>
          <Space size={10}>
            <span className="brand-mark">S</span>
            <Typography.Text strong>SeatFlow</Typography.Text>
          </Space>
        </NavLink>

        <Menu
          className="desktop-nav"
          mode="horizontal"
          selectedKeys={selectedKey(location.pathname, visibleNavItems)}
          items={buildMenuItems(visibleNavItems)}
          onClick={handleMenuClick}
        />

        <Button
          aria-label="Open navigation"
          className="mobile-nav-trigger"
          icon={<MenuOutlined />}
          type="text"
          onClick={() => setDrawerOpen(true)}
        />

        <Drawer
          title="SeatFlow"
          open={drawerOpen}
          onClose={closeDrawer}
          placement="right"
          size="default"
        >
          <Menu
            mode="inline"
            selectedKeys={selectedKey(location.pathname, visibleNavItems)}
            items={buildMenuItems(visibleNavItems, closeDrawer)}
            onClick={handleMenuClick}
          />
        </Drawer>
      </Header>

      <Content className="app-content">
        <Outlet />
      </Content>

      <Footer className="app-footer">SeatFlow</Footer>
    </Layout>
  )
}
