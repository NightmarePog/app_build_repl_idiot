<script lang="ts">
  import '../app.css';
  import { resolve } from '$app/paths';
  import { FileText } from '@lucide/svelte';
  import AppShell from '$lib/components/AppShell.svelte';
  import CommandPalette, { type PaletteItem } from '$lib/components/CommandPalette.svelte';
  import ProjectCredits from '$lib/features/about/ProjectCredits.svelte';
  import FooterLines from '$lib/features/network/FooterLines.svelte';
  import NavMenus from '$lib/features/network/NavMenus.svelte';
  import LineBadge from '$lib/features/network/LineBadge.svelte';
  import { describeStop, LINES, LINE_IDS, linesOf, type LineId } from '$lib/features/network/lines';
  import { loadStops, stopsList } from '$lib/features/network/stops-store.svelte';
  import { rememberStop } from '$lib/features/stops/selected-stop';
  let { children } = $props();

  let paletteOpen = $state(false);

  type Item = PaletteItem & { lines: LineId[]; page?: boolean };

  const pages: Item[] = [
    ['Domů', 'Úvodní stránka', resolve('/')],
    ['Mapa zastávek', 'Zastávky na mapě s vyhledáváním a filtry', resolve('/stops')],
    ['Kontakt', 'Napište týmu', resolve('/contact')],
    ['Podmínky', 'Podmínky používání', resolve('/terms')]
  ].map(([label, sub, href]) => ({
    key: `page-${href}`,
    label,
    sub,
    href,
    lines: [],
    page: true,
    suggested: true
  }));

  const lineItems: Item[] = LINE_IDS.map((id) => {
    const stops = LINES[id].stops;
    return {
      key: `line-${id}`,
      label: `Linka ${id}`,
      sub: `${stops[0]} → ${stops[stops.length - 1]}, ${stops.length} zastávek`,
      href: `${resolve('/stops')}?line=${id}`,
      lines: [id],
      suggested: true
    };
  });

  const stops = $derived(stopsList() ?? []);
  const paletteItems = $derived<Item[]>([
    ...stops.map((stop, index) => ({
      key: `stop-${stop.id}`,
      label: stop.name,
      sub: describeStop(stop.name),
      href: resolve('/stops/[id]', { id: String(stop.id) }),
      lines: linesOf(stop.name),
      suggested: index < 6
    })),
    ...lineItems,
    ...pages
  ]);

  function choose(item: PaletteItem) {
    const stop = stops.find((s) => `stop-${s.id}` === item.key);
    if (stop) rememberStop(stop);
  }
</script>

{#snippet footerCredits()}
  <ProjectCredits />
{/snippet}

{#snippet footerLines()}
  <FooterLines />
{/snippet}

{#snippet menus(kind: 'stops' | 'lines', close: () => void)}
  <NavMenus {kind} id={`menu-${kind}`} onclose={close} />
{/snippet}

{#snippet paletteIcon(item: PaletteItem)}
  {@const entry = paletteItems.find((candidate) => candidate.key === item.key)}
  {#if entry?.page}
    <FileText size={16} />
  {:else if entry?.lines.length}
    <span class="flex gap-0.5">
      {#each entry.lines as id (id)}<LineBadge {id} />{/each}
    </span>
  {:else}
    <span class="ml-1.5 size-2.5 rounded-full border-2 border-muted-foreground"></span>
  {/if}
{/snippet}

<AppShell {menus} {footerCredits} {footerLines} bind:paletteOpen>{@render children()}</AppShell>
<CommandPalette
  bind:open={paletteOpen}
  items={paletteItems}
  onopen={() => void loadStops()}
  onchoose={choose}
  icon={paletteIcon}
/>
