<script lang="ts">
  import { Button } from '$lib/ui/button';

  let {
    title,
    description,
    headingLevel = 2,
    error = false,
    onretry
  }: {
    title: string;
    description?: string;
    headingLevel?: 1 | 2;
    error?: boolean;
    onretry?: () => void;
  } = $props();

  const text = $derived(
    description ?? (error && onretry ? 'Zkontrolujte připojení a zkuste to znovu.' : undefined)
  );
</script>

<div class="page-state" role={error ? undefined : 'status'}>
  <svelte:element this={headingLevel === 1 ? 'h1' : 'h2'} role={error ? 'alert' : undefined}
    >{title}</svelte:element
  >
  {#if text}<p>{text}</p>{/if}
  {#if onretry}
    <Button variant="outline" onclick={onretry}>Zkusit znovu</Button>
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
