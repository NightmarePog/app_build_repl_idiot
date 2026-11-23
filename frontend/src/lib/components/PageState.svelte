<script lang="ts">
  import { Button } from '$lib/ui/button';

  let {
    kind,
    title,
    description,
    headingLevel = 2,
    error = false,
    onretry,
    href,
    linkLabel
  }: {
    /** Type label above the heading (Spoj, Linka, Zastávka), so a 404 still says what was asked for. */
    kind?: string;
    title: string;
    description?: string;
    headingLevel?: 1 | 2;
    error?: boolean;
    onretry?: () => void;
    /** Optional way out, e.g. back to an overview after a 404. */
    href?: string;
    linkLabel?: string;
  } = $props();

  const text = $derived(
    description ?? (error && onretry ? 'Zkontrolujte připojení a zkuste to znovu.' : undefined)
  );
</script>

<div class="page-state" class:labelled={kind} role={error ? undefined : 'status'}>
  {#if kind}<span class="kind">{kind}</span>{/if}
  <svelte:element this={headingLevel === 1 ? 'h1' : 'h2'} role={error ? 'alert' : undefined}
    >{title}</svelte:element
  >
  {#if text}<p>{text}</p>{/if}
  {#if onretry}
    <Button variant="outline" onclick={onretry}>Zkusit znovu</Button>
  {/if}
  {#if href && linkLabel}
    <Button variant="outline" {href}>{linkLabel}</Button>
  {/if}
</div>

<style>
  .page-state {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
    padding-top: 1rem;
    border-top: 1px solid var(--border);
  }
  .page-state.labelled {
    border-top: none;
    padding-top: 0;
  }
  .kind {
    font-size: 0.75rem;
    font-weight: var(--weight-strong);
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--brand);
  }
  .page-state :global(h1),
  .page-state :global(h2) {
    font-size: 1.25rem;
    line-height: 1.25;
  }
  .page-state :global(h1) {
    font-size: 1.75rem;
  }
  p {
    max-width: 36rem;
    color: var(--muted-foreground);
  }
  .page-state :global([data-slot='button']) {
    margin-top: 0.5rem;
  }
</style>
