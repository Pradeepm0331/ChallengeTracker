import { useEffect, useState, useCallback, useMemo } from 'react'
import './styles.css'

const BASE = import.meta.env.VITE_API_URL || ''

const api = async (path, opts = {}) => {
  const token = localStorage.getItem('token')
  const res = await fetch(BASE + '/api' + path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: 'Bearer ' + token }) },
    body: opts.body && JSON.stringify(opts.body),
  })
  if (res.status === 401 && token) { localStorage.removeItem('token'); location.reload() }
  if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || 'Something went wrong. Try again.')
  return res.json()
}
const iso = (d) => d.toLocaleDateString('en-CA')
const parse = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d) }

export default function App() {
  const [user, setUser] = useState(localStorage.getItem('token') ? localStorage.getItem('username') : null)
  const logout = () => { localStorage.clear(); setUser(null) }
  if (!user) return <Auth onAuth={setUser} />
  return <Home user={user} logout={logout} />
}

function Auth({ onAuth }) {
  const [mode, setMode] = useState('login'); const [f, setF] = useState({ username: '', password: '' }); const [err, setErr] = useState('')
  const submit = async () => {
    try {
      const r = await api('/auth/' + mode, { method: 'POST', body: f })
      localStorage.setItem('token', r.token); localStorage.setItem('username', r.username); onAuth(r.username)
    } catch (e) { setErr(e.message) }
  }
  return (
    <div className="auth card">
      <h1>100-Day Challenge Tracker</h1>
      <p className="muted">Plan a task list for every day, check it off, and keep the record.</p>
      <div className="row" style={{ marginTop: 16 }}>
        <input placeholder="Username" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} />
        <input type="password" placeholder="Password (6+ characters)" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })}
          onKeyDown={(e) => e.key === 'Enter' && submit()} />
      </div>
      <div className="row" style={{ marginTop: 12 }}>
        <button onClick={submit}>{mode === 'login' ? 'Log in' : 'Create account'}</button>
        <button className="ghost" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setErr('') }}>
          {mode === 'login' ? 'New here? Sign up' : 'Have an account? Log in'}</button>
      </div>
      {err && <p className="err">{err}</p>}
    </div>
  )
}

function Home({ user, logout }) {
  const [list, setList] = useState([]); const [open, setOpen] = useState(null)
  const [name, setName] = useState(''); const [start, setStart] = useState(iso(new Date())); const [err, setErr] = useState('')
  const load = useCallback(() => api('/challenges').then(setList).catch((e) => setErr(e.message)), [])
  useEffect(() => { load() }, [load])
  const create = async () => {
    try { const c = await api('/challenges', { method: 'POST', body: { name, startDate: start } }); setName(''); await load(); setOpen(c) }
    catch (e) { setErr(e.message) }
  }
  return (
    <div className="wrap">
      <div className="top">
        <h1>{open ? open.name : '100-Day Challenge Tracker'}</h1>
        <div className="row"><span className="muted">{user}</span>
          {open && <button className="ghost" onClick={() => setOpen(null)}>All challenges</button>}
          <button className="ghost" onClick={logout}>Log out</button></div>
      </div>
      {open ? <ChallengeView c={open} /> : (
        <div className="card">
          <h2>Start a challenge</h2>
          <div className="row" style={{ marginTop: 12 }}>
            <input placeholder="Challenge name" value={name} onChange={(e) => setName(e.target.value)} />
            <label className="row muted">Start date <input type="date" value={start} onChange={(e) => setStart(e.target.value)} /></label>
            <button onClick={create}>Generate 100 days</button>
          </div>
          {err && <p className="err">{err}</p>}
          <div className="list">
            {list.length === 0 && <p className="muted">No challenges yet. Pick a start date above to create your first one.</p>}
            {list.map((c) => (
              <button key={c.id} className="item" onClick={() => setOpen(c)}>
                <span>{c.name}</span><span className="muted">Starts {c.startDate}</span></button>))}
          </div>
        </div>
      )}
    </div>
  )
}

function ChallengeView({ c }) {
  const [days, setDays] = useState([]); const [stats, setStats] = useState(null); const [sel, setSel] = useState(1)
  const refresh = useCallback(async () => {
    const [d, s] = await Promise.all([api(`/challenges/${c.id}/days`), api(`/challenges/${c.id}/analytics`)])
    setDays(d); setStats(s)
  }, [c.id])
  useEffect(() => {
    refresh()
    const n = Math.round((parse(iso(new Date())) - parse(c.startDate)) / 864e5) + 1
    setSel(Math.min(100, Math.max(1, n)))
  }, [c, refresh])
  return (
    <>
      {stats && (
        <div className="stats">
          <div className="stat hero">
            <b>{stats.overallPercent}%</b>
            <span>{stats.totalCompleted} of {stats.totalPlanned} planned tasks done</span>
            <div className="bar"><i style={{ width: stats.overallPercent + '%' }} /></div>
          </div>
          <div className="stat"><b>{stats.daysCompleted}/100</b><span className="muted">days fully completed</span></div>
          <div className="stat"><b>{stats.currentStreak}</b><span className="muted">day current streak</span></div>
          <div className="stat"><b>{stats.bestStreak}</b><span className="muted">day best streak</span></div>
        </div>)}
      <div className="grid">
        <Calendar days={days} sel={sel} setSel={setSel} />
        <DayPanel cid={c.id} n={sel} onChange={refresh} />
      </div>
    </>
  )
}

function Calendar({ days, sel, setSel }) {
  const byDate = useMemo(() => Object.fromEntries(days.map((d) => [d.date, d])), [days])
  const months = useMemo(() => {
    if (!days.length) return []
    const a = parse(days[0].date), b = parse(days[days.length - 1].date), out = []
    for (let d = new Date(a.getFullYear(), a.getMonth(), 1); d <= b; d = new Date(d.getFullYear(), d.getMonth() + 1, 1)) out.push(d)
    return out
  }, [days])
  const today = iso(new Date())
  return (
    <div className="card">
      {months.map((m) => {
        const count = new Date(m.getFullYear(), m.getMonth() + 1, 0).getDate()
        const cells = [...Array(m.getDay()).fill(null), ...Array.from({ length: count }, (_, i) => new Date(m.getFullYear(), m.getMonth(), i + 1))]
        return (
          <div className="month" key={+m}>
            <h3>{m.toLocaleString('en', { month: 'long', year: 'numeric' })}</h3>
            <div className="cal">
              {'SMTWTFS'.split('').map((d, i) => <div className="dow" key={i}>{d}</div>)}
              {cells.map((d, i) => {
                if (!d) return <div key={i} />
                const h = byDate[iso(d)]
                const cls = !h ? '' : 'in ' + (h.complete ? 'done' : h.percent >= 50 ? 'p2' : h.percent > 0 ? 'p1' : '')
                return (
                  <button key={i} disabled={!h} onClick={() => setSel(h.dayNumber)}
                    aria-label={h ? `Day ${h.dayNumber}, ${h.percent}% done` : undefined}
                    className={`cell ${cls} ${h && h.dayNumber === sel ? 'sel' : ''} ${iso(d) === today ? 'today' : ''}`}>
                    <span>{d.getDate()}</span>{h && <small>Day {h.dayNumber}</small>}
                  </button>)
              })}
            </div>
          </div>)
      })}
    </div>
  )
}

function Ring({ value, size = 92 }) {
  const r = 40, c = 2 * Math.PI * r
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" className="ring" role="img" aria-label={`${value}% complete`}>
      <circle cx="50" cy="50" r={r} className="ring-bg" />
      <circle cx="50" cy="50" r={r} className="ring-fg" strokeDasharray={c} strokeDashoffset={c * (1 - value / 100)} />
      <text x="50" y="57" textAnchor="middle" className="ring-t">{value}%</text>
    </svg>
  )
}

function DayPanel({ cid, n, onChange }) {
  const [data, setData] = useState(null); const [title, setTitle] = useState(''); const [err, setErr] = useState('')
  const [editId, setEditId] = useState(null); const [editTitle, setEditTitle] = useState('')
  const load = useCallback(() => api(`/challenges/${cid}/days/${n}`).then(setData).catch((e) => setErr(e.message)), [cid, n])
  useEffect(() => { setData(null); setErr(''); setEditId(null); load() }, [load])

  const add = async () => {
    if (!title.trim()) return
    try { await api(`/challenges/${cid}/days/${n}/tasks`, { method: 'POST', body: { title } }); setTitle(''); await load(); onChange() }
    catch (e) { setErr(e.message) }
  }
  const toggle = async (t) => {
    try { await api('/tasks/' + t.id, { method: 'PATCH', body: { completed: !t.completed } }); await load(); onChange() }
    catch (e) { setErr(e.message) }
  }
  const startEdit = (t) => { setEditId(t.id); setEditTitle(t.title) }
  const saveEdit = async (t) => {
    if (!editTitle.trim()) return
    try { await api('/tasks/' + t.id, { method: 'PUT', body: { title: editTitle } }); setEditId(null); await load() }
    catch (e) { setErr(e.message) }
  }
  const remove = async (t) => {
    if (!window.confirm(`Delete "${t.title}"?`)) return
    try { await api('/tasks/' + t.id, { method: 'DELETE' }); await load(); onChange() }
    catch (e) { setErr(e.message) }
  }

  if (!data) return <div className="card">{err ? <p className="err">{err}</p> : 'Loading…'}</div>
  const { day, tasks } = data
  return (
    <div className="card">
      <div className="day-head">
        <div>
          <h2>Day {day.dayNumber}</h2>
          <p className="muted">{parse(day.date).toLocaleDateString('en', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}</p>
        </div>
        <Ring value={day.percent} />
      </div>
      <div style={{ marginTop: 8 }}>
        {tasks.length === 0 && <p className="muted">Nothing planned for this day yet. Add your first task below.</p>}
        {tasks.map((t) => (
          <div key={t.id} className={'task ' + (t.completed ? 'd' : '')}>
            <input type="checkbox" checked={t.completed} onChange={() => toggle(t)} aria-label={`Mark "${t.title}" done`} />
            {editId === t.id ? (
              <>
                <input className="edit" autoFocus value={editTitle} onChange={(e) => setEditTitle(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') saveEdit(t); if (e.key === 'Escape') setEditId(null) }} />
                <button className="mini" onClick={() => saveEdit(t)}>Save</button>
                <button className="mini ghost" onClick={() => setEditId(null)}>Cancel</button>
              </>
            ) : (
              <>
                <span>{t.title}</span>
                <div className="acts">
                  <button className="mini ghost" onClick={() => startEdit(t)}>Edit</button>
                  <button className="mini danger" onClick={() => remove(t)}>Delete</button>
                </div>
              </>
            )}
          </div>))}
      </div>
      <div className="row" style={{ marginTop: 12 }}>
        <input style={{ flex: 1 }} placeholder="Add a task" value={title} onChange={(e) => setTitle(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && add()} />
        <button onClick={add}>Add task</button>
      </div>
      {err && <p className="err">{err}</p>}
      <div className="summary">
        <b>Summary</b><br />
        Planned {day.planned} · Completed {day.completed} · Remaining {day.planned - day.completed}
        <div className="muted">{day.complete ? 'Day complete. Nice work.' : 'Last updated ' + new Date(day.updatedAt).toLocaleString()}</div>
      </div>
    </div>
  )
}