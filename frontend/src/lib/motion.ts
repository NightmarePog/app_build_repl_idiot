import type { Attachment } from 'svelte/attachments';
import { animate } from 'motion';

/** Duration for Svelte transitions: zero when the user prefers reduced motion. */
export function motionMs(ms: number) {
  return prefersReducedMotion() ? 0 : ms;
}

export function prefersReducedMotion() {
  return (
    typeof window === 'undefined' || window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/**
 * Scroll reveal: an element that starts below the fold fades and rises into place when it scrolls
 * into view. Content already on screen at load is left alone (no flash after hydration), and
 * without JavaScript or with reduced motion everything is simply visible.
 */
export function reveal({
  delay = 0,
  y = 18
}: { delay?: number; y?: number } = {}): Attachment<HTMLElement> {
  return (node) => {
    if (prefersReducedMotion()) return;
    if (node.getBoundingClientRect().top < window.innerHeight) return;

    node.style.opacity = '0';
    node.style.transform = `translateY(${y}px)`;
    let controls: ReturnType<typeof animate> | undefined;
    const seen = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        seen.disconnect();
        controls = animate(
          node,
          { opacity: [0, 1], transform: [`translateY(${y}px)`, 'translateY(0px)'] },
          { type: 'spring', duration: 0.7, bounce: 0, delay: Math.min(delay, 0.4) }
        );
      },
      { rootMargin: '0px 0px -8% 0px' }
    );
    seen.observe(node);
    return () => {
      seen.disconnect();
      controls?.cancel();
      node.style.opacity = '';
      node.style.transform = '';
    };
  };
}
