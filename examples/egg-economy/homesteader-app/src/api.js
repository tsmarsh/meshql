const API_BASE = import.meta.env.PROD ? '/api' : '/api'

export async function graphqlQuery(endpoint, query, field) {
  const res = await fetch(`${API_BASE}${endpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query })
  })
  const json = await res.json()
  if (json.errors) throw new Error(json.errors[0].message)
  return json.data[field]
}

export async function restPost(endpoint, data) {
  const res = await fetch(`${API_BASE}${endpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  if (!res.ok) throw new Error(`POST failed (${res.status})`)
  return res
}
