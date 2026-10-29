<script lang="ts">
  import { AMENITIES, type AmenityValue } from './amenities';

  let {
    value,
    onchange,
    id
  }: { value: AmenityValue[]; onchange: (v: AmenityValue[]) => void; id?: string } = $props();

  function toggle(amenity: AmenityValue) {
    const next = value.includes(amenity)
      ? value.filter((item) => item !== amenity)
      : [...value, amenity];
    // Keep a stable order so the URL does not depend on click order.
    onchange(AMENITIES.map(({ value }) => value).filter((item) => next.includes(item)));
  }
</script>

<div class="amenity-filter" role="group" aria-label="Vybavení zastávky" {id}>
  {#each AMENITIES as amenity (amenity.value)}
    {@const pressed = value.includes(amenity.value)}
    <button
      type="button"
      class="amenity-toggle"
      aria-pressed={pressed}
      onclick={() => toggle(amenity.value)}
    >
      <amenity.icon aria-hidden="true" size={16} strokeWidth={1.75} />
      {amenity.label}
    </button>
  {/each}
</div>

<style>
  .amenity-filter {
    display: flex;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .amenity-toggle {
    display: inline-flex;
    min-height: 2.75rem;
    align-items: center;
    gap: 0.5rem;
    padding: 0 0.875rem;
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    background: var(--background);
    color: var(--muted-foreground);
    font-size: 0.875rem;
    font-weight: 500;
    cursor: pointer;
    transition:
      background-color var(--motion-fast) var(--ease-out),
      border-color var(--motion-fast) var(--ease-out),
      color var(--motion-fast) var(--ease-out);
  }
  .amenity-toggle:hover {
    background: var(--muted);
    color: var(--foreground);
  }
  .amenity-toggle[aria-pressed='true'] {
    border-color: var(--primary);
    background: var(--accent);
    color: var(--foreground);
    font-weight: 600;
  }
  .amenity-toggle:focus-visible {
    outline: 2px solid var(--ring, var(--primary));
    outline-offset: 2px;
  }
  @media (prefers-reduced-motion: reduce) {
    .amenity-toggle {
      transition: none;
    }
  }
</style>
