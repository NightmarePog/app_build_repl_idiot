<script lang="ts">
  import { Skeleton } from '$lib/ui/skeleton';
  let { label, detail = false }: { label: string; detail?: boolean } = $props();
</script>

<p role="status" class="sr-only">{label}</p>

{#if detail}
  <div class="grid gap-8 py-2 lg:grid-cols-[1.55fr_1fr]" aria-hidden="true">
    <Skeleton class="aspect-[3/2] w-full rounded-lg" />
    <div class="flex flex-col gap-6">
      <Skeleton class="h-10 w-2/3" />
      {#each [0, 1, 2] as item (item)}
        <Skeleton class="h-12 w-full" />
      {/each}
    </div>
  </div>
{:else}
  <div class="grid gap-3 py-2" aria-hidden="true">
    {#each [0, 1, 2, 3] as item (item)}
      <div class="grid grid-cols-[1.75rem_minmax(0,1fr)] items-center gap-3">
        <Skeleton class="size-3 justify-self-center rounded-full" />
        <div class="flex flex-col gap-2">
          <Skeleton class="h-4 w-2/3" />
          <Skeleton class="h-3 w-1/3" />
        </div>
      </div>
    {/each}
  </div>
{/if}
