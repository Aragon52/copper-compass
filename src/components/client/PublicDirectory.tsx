import { useEffect, useMemo, useState } from 'react';
import { RefreshCw, Star, Plus, ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { countryName, directorySource, fetchDirectory, ratingsKey, readRatings, sortDirectory, type DirectorySort, type Ratings } from '@/lib/server-directory';
import type { Protocol, ServerNode } from '@/lib/v2ray';

export function PublicDirectory({ onImport, protocols }: { onImport: (node: ServerNode) => void; protocols: Protocol[] }) {
  const [country, setCountry] = useState(''); const [sort, setSort] = useState<DirectorySort>('name');
  const [search, setSearch] = useState(''); const [nodes, setNodes] = useState<ServerNode[]>([]);
  const [ratings, setRatings] = useState<Ratings>({}); const [error, setError] = useState('');
  const [busy, setBusy] = useState(false); const [refresh, setRefresh] = useState(0);
  const [summary, setSummary] = useState(''); const [limit, setLimit] = useState(30); const [imported, setImported] = useState<string[]>([]);
  useEffect(() => { try { setRatings(readRatings(localStorage.getItem(ratingsKey))); } catch { setError('Personal ratings cannot be saved in this browser.'); } }, []);
  useEffect(() => {
    const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 20000); let active = true;
    setBusy(true); setError(''); setNodes([]); setSummary(''); setLimit(30);
    void fetchDirectory(country, controller.signal).then(result => {
      if (!active) return;
      setNodes(result.nodes);
      setSummary(`${result.nodes.length} supported · ${result.skipped} skipped${result.truncated ? ' · feed limited' : ''} · fetched ${new Date(result.fetchedAt).toLocaleTimeString()}`);
    }).catch(() => { if (active) setError('Public directory unavailable or timed out. Refresh or try another country.'); }).finally(() => { if (active) setBusy(false); clearTimeout(timeout); });
    return () => { active = false; clearTimeout(timeout); controller.abort(); };
  }, [country, refresh]);
  const visible = useMemo(() => sortDirectory(nodes.filter(n => protocols.includes(n.protocol) && `${n.name} ${n.country} ${n.protocol}`.toLowerCase().includes(search.toLowerCase())), sort, ratings), [nodes, protocols, search, sort, ratings]);
  function rate(id: string, value: number) {
    const next = { ...ratings, [id]: value }; setRatings(next);
    try { localStorage.setItem(ratingsKey, JSON.stringify(next)); } catch { setError('Rating changed for this session only; device storage is unavailable.'); }
  }
  return <div className="public-directory">
    <div className="directory-attribution"><a href={directorySource.repository} target="_blank" rel="noreferrer">FastNodes ↗</a><Button variant="outline" size="icon" className="keycap" aria-label="Refresh public directory" title="Refresh public directory" disabled={busy} onClick={() => setRefresh(v => v + 1)}><RefreshCw className={busy ? 'animate-spin' : ''} /></Button></div>
    <p className="directory-warning"><ShieldAlert size={16} />UNTRUSTED PUBLIC SERVERS · Operators may observe traffic. Availability and privacy are not guaranteed.</p>
    <div className="directory-controls"><label className="field-label">LOCATION<select className="engraved-input" aria-label="Directory location" value={country} onChange={e => setCountry(e.target.value)}><option value="">Top feed · Unknown location</option>{['AE','AU','CA','DE','FI','FR','GB','HK','IN','IR','JP','KR','NL','SE','SG','TR','US'].map(code => <option key={code} value={code}>{countryName(code)} · source-reported</option>)}</select></label><label className="field-label">SORT<select className="engraved-input" aria-label="Directory sort" value={sort} onChange={e => setSort(e.target.value as DirectorySort)}><option value="name">Name</option><option value="location">Location</option><option value="latency">Latency · unmeasured last</option><option value="rating">My rating</option></select></label></div>
    <input className="engraved-input" aria-label="Search public servers" placeholder="Search name, protocol or location" value={search} onChange={e => { setSearch(e.target.value); setLimit(30); }} />
    <p className="directory-meta">{busy ? 'Loading public directory…' : summary}</p>
    <p className="directory-meta">Device latency unmeasured · Android VPN service unavailable. Country feeds reflect the source’s location claims.</p>
    {error && <p className="ai-error" role="alert">{error}</p>}
    {!busy && !error && !visible.length && <p className="directory-meta">No matching supported servers.</p>}
    <div className="directory-list">{visible.slice(0, limit).map(node => <div className="directory-row" key={node.id}>
      <div className="directory-node"><span>{node.flag}</span><div><strong>{node.name}</strong><small>{node.country} · UNTRUSTED · Unmeasured</small></div><Button variant="outline" size="icon" className="keycap" title="Import server" aria-label={`Import ${node.name}`} disabled={imported.includes(node.id)} onClick={() => { onImport(node); setImported(old => [...old, node.id]); }}><Plus /></Button></div>
      <div className="directory-rating"><span>My rating</span>{[1,2,3,4,5].map(value => <Button variant="ghost" size="icon" key={value} className={value <= (ratings[node.id] || 0) ? 'rating-on' : ''} aria-label={`Rate ${node.name} ${value} stars`} aria-pressed={ratings[node.id] === value} title={`${value} stars`} onClick={() => rate(node.id, value)}><Star /></Button>)}</div>
    </div>)}</div>
    {visible.length > limit && <Button variant="outline" className="keycap" onClick={() => setLimit(old => old + 30)}>SHOW MORE ({visible.length - limit})</Button>}
  </div>;
}