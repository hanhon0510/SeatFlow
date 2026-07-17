import { Button, Result } from 'antd'
import { Link } from 'react-router-dom'
import { ROUTES } from '../../../shared/constants/routes'

export function NotFoundPage() {
  return (
    <Result
      status="404"
      title="404"
      subTitle="Page not found"
      extra={
        <Button type="primary">
          <Link to={ROUTES.home}>Go home</Link>
        </Button>
      }
    />
  )
}
