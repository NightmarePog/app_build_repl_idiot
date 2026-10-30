<script lang="ts">
  import { cn } from '$lib/utils';

  let {
    src,
    name,
    eager = false,
    class: className,
    id,
    transition = false
  }: {
    src: string | null;
    name: string;
    eager?: boolean;
    class?: string;
    id?: number | string;
    transition?: boolean;
  } = $props();

  let failedSrc = $state<string | null>();

  /**
   * Fade the photo in when it arrives. Visible by default (no script, or already cached), it is
   * only hidden while still downloading, so a page-transition snapshot never captures a blank.
   */
  const fadeIn = (img: HTMLImageElement) => {
    if (img.complete) return;
    img.classList.add('pending');
    const show = () => img.classList.remove('pending');
    img.addEventListener('load', show, { once: true });
    img.addEventListener('error', show, { once: true });
    return () => {
      img.removeEventListener('load', show);
      img.removeEventListener('error', show);
    };
  };
</script>

<div
  class={cn(
    'stop-image-frame relative flex aspect-[3/2] items-center justify-center overflow-hidden bg-muted',
    className
  )}
  style:view-transition-name={transition && id !== undefined
    ? `stop-image-${String(id).replace(/[^a-zA-Z0-9_-]/g, '-')}`
    : undefined}
>
  {#if src && src !== failedSrc}
    <img
      {src}
      alt={`Zastávka ${name}`}
      width="1536"
      height="1024"
      loading={eager ? 'eager' : 'lazy'}
      fetchpriority={eager ? 'high' : 'auto'}
      decoding="async"
      class="stop-image size-full object-cover"
      onerror={() => (failedSrc = src)}
      {@attach fadeIn}
    />
  {:else}
    <p class="px-6 text-center text-muted-foreground">Obrázek zastávky není k dispozici</p>
  {/if}
</div>

<style>
  .stop-image {
    transition:
      opacity var(--motion-slow) var(--ease-out),
      transform var(--motion-slow) var(--ease-out);
  }
  .stop-image:global(.pending) {
    opacity: 0;
    transform: scale(1.02);
  }
  @media (prefers-reduced-motion: reduce) {
    .stop-image {
      transition: none;
    }
  }
</style>
