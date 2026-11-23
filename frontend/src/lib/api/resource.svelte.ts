/**
 * One fetch pattern for every page: abort on change, 10 s timeout, retry, and a typed failure.
 * `load` throws a `ResourceError` ('missing' for a 404, anything else 'unavailable'); `unwrap`
 * does that mapping for a generated SDK call. While a retry loads, the previous data stays on
 * screen (`loading` is true). When `key` changes (another trip, another line) the old data is
 * dropped at once, so a page never shows the previous record under the new id.
 */
export class ResourceError extends Error {
  constructor(readonly kind: 'missing' | 'unavailable') {
    super(kind);
  }
}

/** The SDK result without throwOnError: data, or a ResourceError for 404 and other failures. */
export async function unwrap<T>(call: Promise<{ data?: T; response?: Response }>): Promise<T> {
  const { data, response } = await call;
  if (response?.status === 404) throw new ResourceError('missing');
  if (data === undefined || !response?.ok) throw new ResourceError('unavailable');
  return data;
}

export function createResource<T>(
  load: (signal: AbortSignal) => Promise<T>,
  key: () => unknown = () => null
) {
  let data = $state<T | null>(null);
  let error = $state<'missing' | 'unavailable' | null>(null);
  let loading = $state(true);
  let attempt = $state(0);
  let lastKey: unknown = undefined;

  $effect(() => {
    void attempt;
    const current = key();
    if (current !== lastKey) {
      data = null;
      lastKey = current;
    }
    loading = true;
    error = null;
    const controller = new AbortController();
    load(AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)]))
      .then((value) => {
        if (!controller.signal.aborted) data = value;
      })
      .catch((cause) => {
        if (controller.signal.aborted) return;
        data = null;
        error = cause instanceof ResourceError ? cause.kind : 'unavailable';
      })
      .finally(() => {
        if (!controller.signal.aborted) loading = false;
      });
    return () => controller.abort();
  });

  return {
    get data() {
      return data;
    },
    get error() {
      return error;
    },
    get loading() {
      return loading;
    },
    retry: () => attempt++
  };
}
