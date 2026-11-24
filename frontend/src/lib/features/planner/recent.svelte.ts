/** The last few from → to searches, kept per browser so they can be repeated with one tap. */
export type Recent = { from: number; to: number };

const KEY = 'planner-recent';
const MAX = 5;

function load(): Recent[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter(
        (r): r is Recent =>
          typeof r?.from === 'number' && typeof r?.to === 'number' && r.from !== r.to
      )
      .slice(0, MAX);
  } catch {
    return [];
  }
}

let items = $state<Recent[]>(typeof localStorage === 'undefined' ? [] : load());

function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(items));
  } catch {
    // Storage blocked (private mode): recents just last for this visit.
  }
}

export function recentSearches(): Recent[] {
  return items;
}

export function rememberSearch(from: number, to: number) {
  const rest = recentSearches().filter((r) => !(r.from === from && r.to === to));
  items = [{ from, to }, ...rest].slice(0, MAX);
  save();
}

export function forgetSearch(from: number, to: number) {
  items = recentSearches().filter((r) => !(r.from === from && r.to === to));
  save();
}
