import { useState } from 'react'
import type { ReactNode } from 'react'
import {
  CalendarOutlined,
  HomeOutlined,
  LockOutlined,
  LoginOutlined,
  MenuOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import { Button, Drawer, Layout, Menu, Space, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { ROUTES } from '../../shared/constants/routes'

const { Header, Content, Footer } = Layout

const navItems = [
  { key: ROUTES.home, label: 'Health', icon: <HomeOutlined /> },
  { key: ROUTES.events, label: 'Events', icon: <CalendarOutlined /> },
  { key: ROUTES.admin, label: 'Admin', icon: <LockOutlined /> },
  { key: ROUTES.login, label: 'Login', icon: <LoginOutlined /> },
  { key: ROUTES.register, label: 'Register', icon: <UserAddOutlined /> },
] satisfies Array<{ key: string; label: string; icon: ReactNode }>

function buildMenuItems(closeDrawer?: () => void): MenuProps['items'] {
  return navItems.map((item) => ({
    key: item.key,
    icon: item.icon,
    label: (
      <NavLink to={item.key} onClick={closeDrawer}>
        {item.label}
      </NavLink>
    ),
  }))
}

function selectedKey(pathname: string) {
  return navItems.some((item) => item.key === pathname) ? [pathname] : []
}

export function AppShell() {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const location = useLocation()

  const closeDrawer = () => setDrawerOpen(false)

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
          selectedKeys={selectedKey(location.pathname)}
          items={buildMenuItems()}
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
          width={280}
        >
          <Menu
            mode="inline"
            selectedKeys={selectedKey(location.pathname)}
            items={buildMenuItems(closeDrawer)}
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
