import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, useRouter } from './router'
import { StoreProvider } from './StoreContext'
import Layout from './components/Layout'
import StoresPage from './pages/StoresPage'
import StoreFormPage from './pages/StoreFormPage'
import ComingSoon from './pages/ComingSoon'
import MenusPage from './pages/MenusPage'
import SalesPage from './pages/SalesPage'
import ReviewsPage from './pages/ReviewsPage'
import ReportPage from './pages/ReportPage'
import './styles.css'

function Routes() {
  const { path } = useRouter()

  let page
  if (path === '/' || path === '/stores') page = <StoresPage />
  else if (path === '/stores/new') page = <StoreFormPage />
  else if (/^\/stores\/\d+$/.test(path)) page = <StoreFormPage storeId={Number(path.split('/')[2])} />
  else if (path === '/menus') page = <MenusPage />
  else if (path === '/sales') page = <SalesPage />
  else if (path === '/reviews') page = <ReviewsPage />
  else if (path === '/report') page = <ReportPage />
  else page = <ComingSoon title="없는 화면이에요" note="왼쪽 메뉴에서 다시 골라 주세요." />

  return <Layout>{page}</Layout>
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <RouterProvider>
      <StoreProvider>
        <Routes />
      </StoreProvider>
    </RouterProvider>
  </StrictMode>,
)
