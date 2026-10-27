// 백엔드 API 호출 모음. 오류는 Spring의 ProblemDetail(detail, errors)을 읽어 메시지로 만든다.

async function request(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  const data = text ? safeJson(text) : null
  if (!res.ok) {
    let message = (data && (data.detail || data.error)) || `요청 실패 (${res.status})`
    if (data && Array.isArray(data.errors) && data.errors.length) {
      message += ' — ' + data.errors.join(', ')
    }
    const err = new Error(message)
    err.status = res.status
    throw err
  }
  return data
}

function safeJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export const api = {
  // 매장
  listStores: () => request('GET', '/api/stores'),
  getStore: (id) => request('GET', `/api/stores/${id}`),
  createStore: (body) => request('POST', '/api/stores', body),
  updateStore: (id, body) => request('PATCH', `/api/stores/${id}`, body),
  deleteStore: (id) => request('DELETE', `/api/stores/${id}`),

  // 장소 검색 (카카오)
  searchPlaces: (query) => request('GET', `/api/places/search?query=${encodeURIComponent(query)}`),
}
