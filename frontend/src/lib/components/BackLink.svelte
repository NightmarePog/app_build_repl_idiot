<script lang="ts">
  import { ArrowLeft } from '@lucide/svelte';

  /** The one way back to a parent list, the same on every detail page. */
  let { href, label }: { href: string; label: string } = $props();
</script>

<!-- Callers pass an already resolved path. -->
<!-- eslint-disable-next-line svelte/no-navigation-without-resolve -->
<a class="back-link" {href}><ArrowLeft class="size-4" aria-hidden="true" />{label}</a>

<style>
  .back-link {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    min-height: 2.75rem;
    color: var(--muted-foreground);
    font-size: var(--fs-sm);
    font-weight: var(--weight-strong);
    transition: color var(--motion-fast) var(--ease-out);
  }
  .back-link:hover {
    color: var(--foreground);
    text-decoration: underline;
    text-underline-offset: 3px;
  }
</style>
