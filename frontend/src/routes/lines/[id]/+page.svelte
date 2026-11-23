<script lang="ts">
  import { page } from '$app/state';
  import LineDetailPage from '$lib/features/lines/LineDetailPage.svelte';
</script>

<LineDetailPage id={page.params.id!} />
