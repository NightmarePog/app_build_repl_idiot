<script lang="ts">
  import StopsPage from '$lib/features/stops/StopsPage.svelte';

  let { children } = $props();
</script>

<!-- Lists, the planner and stop/line/trip details are views of one map page; the layout keeps the map mounted between them. -->
<StopsPage>{@render children()}</StopsPage>
