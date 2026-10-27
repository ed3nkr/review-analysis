import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, useRouter } from './router'
import { StoreProvider } from './StoreContext'
import Layout from './components/Layout'
import StoresPage from './pages/StoresPage'
import StoreFormPage from './pages/StoreFormPage'
import ComingSoon from './pages/ComingSoon'
import './styles.css'

function Routes() {
  const { path } = useRouter()

  let page
  if (path === '/' || path === '/stores') page = <StoresPage />
  else if (path === '/stores/new') page = <StoreFormPage />
  else if (/^\/stores\/\d+$/.test(path)) page = <StoreFormPage storeId={Number(path.split('/')[2])} />
  else if (path === '/menus') page = <ComingSoon title="메뉴 관리" note="POS 메뉴를 메뉴군으로 묶고, 리뷰에서 부르는 이름을 등록하는 화면이에요." />
  else if (path === '/sales') page = <ComingSoon title="매출 업로드" note="POS에서 내려받은 매출 CSV를 올리는 화면이에요." />
  else if (path === '/reviews') page = <ComingSoon title="리뷰 분석" note="네이버 플레이스 리뷰를 모아 정리한 결과를 보여 줄 화면이에요." />
  else if (path === '/report') page = <ComingSoon title="연계 분석 리포트" note="리뷰와 매출이 어긋나는 지점을 9개 질문으로 보여 줄 화면이에요." />
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
