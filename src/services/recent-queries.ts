export type SearchStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

export function browserSearchStorage(): SearchStorage | null {
  try { return typeof window === 'undefined' ? null : window.localStorage; }
  catch { return null; }
}

export function recentQueries(storage: SearchStorage | null, account: string): string[] {
  if (!storage || !account) return [];
  try {
    const value: unknown = JSON.parse(storage.getItem('cartiq.search.v1.' + account) || '[]');
    return Array.isArray(value) ? value.filter((s): s is string =>
      typeof s === 'string' && s.length > 0 && s.length <= 100).slice(0, 5) : [];
  } catch { return []; }
}

export function rememberQuery(storage: SearchStorage | null, account: string, query: string, previous: string[]) {
  const term = query.trim().slice(0, 100);
  if (!term) return previous;
  const next = [term, ...previous.filter(s => s.toLowerCase() !== term.toLowerCase())].slice(0, 5);
  try { if (account) storage?.setItem('cartiq.search.v1.' + account, JSON.stringify(next)); } catch {}
  return next;
}

export function clearRecentQueries(storage: SearchStorage | null, account: string) {
  try { storage?.removeItem('cartiq.search.v1.' + account); storage?.removeItem('cartiq.comparisons.v1.' + account); } catch {}
}
type ComparisonBookmark = {scope:string; id:string; expires:number};
function bookmarks(storage: SearchStorage | null, account: string): ComparisonBookmark[] {
  if (!account || !storage) return [];
  try {
    const value: unknown = JSON.parse(storage.getItem('cartiq.comparisons.v1.' + account) || '[]');
    return Array.isArray(value) ? value.filter((v): v is ComparisonBookmark =>
      v && typeof v.scope === 'string' && typeof v.id === 'string' &&
      /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(v.id) && Number.isFinite(v.expires) && v.expires > Date.now()).slice(0, 5) : [];
  } catch { return []; }
}

export function comparisonBookmark(storage: SearchStorage | null, account: string, scope: string) {
  return bookmarks(storage, account).find(entry => entry.scope === scope)?.id;
}

export function saveComparisonBookmark(storage: SearchStorage | null, account: string, scope: string, id: string, expires: number) {
  if (!account || !storage) return;
  const entries = bookmarks(storage, account).filter(entry => entry.scope !== scope);
  try { storage.setItem('cartiq.comparisons.v1.' + account, JSON.stringify([{scope,id,expires}, ...entries].slice(0, 5))); } catch {}
}
