<script lang="ts">
  import type { Component, Snippet } from 'svelte';

  /**
   * One header for every page: a type tag (what kind of thing this is), the title, a meta line.
   * The tag is what tells the passenger whether they look at a line, a stop or a trip.
   */
  let {
    kind,
    icon: Icon,
    title,
    lead,
    meta,
    back,
    color
  }: {
    kind: string;
    icon: Component<{ class?: string; 'aria-hidden'?: boolean | 'true' }>;
    title: string;
    /** Rendered before the title inside the h1, e.g. a line plate. */
    lead?: Snippet;
    meta?: Snippet;
    /** Back link, always above the type tag so every detail page shares one layout. */
    back?: Snippet;
    /** Tag colour; defaults to the primary colour. */
    color?: string;
  } = $props();
</script>

<header class="page-header">
  {#if back}<div class="back-row">{@render back()}</div>{/if}
  <p class="type-tag" style:--tag={color} aria-hidden="true">
    <Icon class="size-4" aria-hidden="true" />
    <span>{kind}</span>
  </p>
  <h1>
    {#if lead}{@render lead()}{/if}
    <span class="sr-only">{kind}: </span><span>{title}</span>
  </h1>
  {#if meta}<div class="meta">{@render meta()}</div>{/if}
</header>

<style>
  .page-header {
    display: grid;
    gap: 0.625rem;
    margin-bottom: 1.5rem;
    scroll-margin-top: calc(var(--header-h) + 1rem);
  }
  .back-row {
    font-size: var(--fs-sm);
  }
  .type-tag {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    justify-self: start;
    padding: 0.3rem 0.7rem 0.3rem 0.55rem;
    border-radius: var(--radius-sm);
    background: color-mix(in oklch, var(--tag, var(--primary)) 16%, var(--background));
    border-left: 4px solid var(--tag, var(--primary));
    color: var(--foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    letter-spacing: var(--tracking-eyebrow);
    text-transform: uppercase;
  }
  h1 {
    display: flex;
    align-items: center;
    gap: 0.875rem;
    font-size: var(--fs-h1);
    font-weight: var(--font-display-weight);
    letter-spacing: var(--tracking-display);
    line-height: var(--lh-tight);
  }
  .meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.375rem 0.75rem;
    color: var(--muted-foreground);
    font-size: var(--fs-base);
  }
</style>
