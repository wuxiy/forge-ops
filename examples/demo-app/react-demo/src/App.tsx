import { useCallback, useEffect, useState } from 'react'

interface ExamRecord {
  id: string
  name: string
  status: string
  time: string
}

export default function App() {
  const [user, setUser] = useState(localStorage.getItem('demo-user') || '')
  const [patientId, setPatientId] = useState('P-10001')
  const [records, setRecords] = useState<ExamRecord[]>([])
  const [loading, setLoading] = useState(false)

  const saveUser = (v: string) => {
    setUser(v)
    localStorage.setItem('demo-user', v)
  }

  const load = useCallback(async () => {
    if (!patientId) return
    setLoading(true)
    try {
      const res = await fetch(`/api/patients/${encodeURIComponent(patientId)}/records`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      setRecords(data.records ?? [])
      setLoading(false)
    } catch {
      // 预埋缺陷：错误时缺少处理（与 vue demo 修复前相同），供反馈演示
    }
  }, [patientId])

  useEffect(() => {
    load()
  }, [load])

  return (
    <div style={{ fontFamily: 'inherit' }}>
      <header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: '#0e7490',
          color: '#fff',
          padding: '10px 24px',
          fontSize: 15,
        }}
      >
        <span>健康平台（ForgeOps React 接入示例）</span>
        <span>
          当前用户：
          <input
            style={{ border: 'none', borderRadius: 4, padding: '4px 8px', marginLeft: 6 }}
            value={user}
            placeholder="你的姓名"
            onChange={(e) => saveUser(e.target.value)}
          />
        </span>
      </header>

      <main style={{ maxWidth: 720, margin: '32px auto', background: '#fff', borderRadius: 12, padding: 24, boxShadow: '0 2px 8px rgba(0,0,0,.06)' }}>
        <h2 style={{ marginTop: 0 }}>检查记录</h2>
        <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
          <input
            style={{ flex: 1, border: '1px solid #d1d5db', borderRadius: 6, padding: '8px 10px' }}
            value={patientId}
            placeholder="患者ID，如 P-10001"
            onChange={(e) => setPatientId(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && load()}
          />
          <button style={{ background: '#0891b2', color: '#fff', border: 'none', borderRadius: 6, padding: '8px 20px', cursor: 'pointer' }} onClick={load}>
            查询
          </button>
        </div>

        {loading ? (
          <div style={{ color: '#6b7280', padding: '24px 0' }}>加载中…</div>
        ) : records.length ? (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                {['检查单号', '项目', '状态', '时间'].map((h) => (
                  <th key={h} style={{ textAlign: 'left', borderBottom: '1px solid #e5e7eb', padding: '10px 8px', fontSize: 14 }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {records.map((r) => (
                <tr key={r.id}>
                  <td style={{ borderBottom: '1px solid #e5e7eb', padding: '10px 8px', fontSize: 14 }}>{r.id}</td>
                  <td style={{ borderBottom: '1px solid #e5e7eb', padding: '10px 8px', fontSize: 14 }}>{r.name}</td>
                  <td style={{ borderBottom: '1px solid #e5e7eb', padding: '10px 8px', fontSize: 14 }}>{r.status === 'DONE' ? '已完成' : '待出报告'}</td>
                  <td style={{ borderBottom: '1px solid #e5e7eb', padding: '10px 8px', fontSize: 14 }}>{r.time?.replace('T', ' ').slice(0, 19)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div style={{ color: '#6b7280', background: '#f9fafb', border: '1px dashed #d1d5db', borderRadius: 8, padding: 24, textAlign: 'center' }}>
            该患者暂无检查记录
          </div>
        )}
      </main>
    </div>
  )
}
