# Direct feedback from the product owner (top priority, overrides reviewer nitpicks)

New items are appended at the bottom. Every fixer must check the items in its area; every reviewer must deduct for any item still not solved.

1. Trip page (/trips/<id>, e.g. 22:49 B with ?od=Ada Exchange) is "really hard to orient in". Observed problems:
   - Title says "22:49 Campus Gate → Hopper Hall" but the user boards at Ada Exchange at 23:06; the header, subtitle ("Jízda 4 min · 7 zastávek") and status card mix the whole trip with the user's segment. Make it obvious: which trip this is, where I board, where I get off, when, and how long MY ride is.
   - The stop list hides 5 earlier stops behind "Zobrazit 5 předchozích zastávek", so the route looks like 2 stops; show the full route with the user's segment highlighted and the rest dimmed (like IDOS / DB trip detail).
   - "Dál z konečné Hopper Hall / Další spoj této trasy 07:00 zítra" is confusing and not useful; replace with something a passenger actually needs (onward connections from the destination, or remove).
   - Labels "přij./odj.", "tady nastupujete", "KONEČNÁ · PŘÍJEZD" are cramped and inconsistent; one clear time column, clear boarding/alighting markers.
   - Overall: one clear reading order top-to-bottom, model it on IDOS spoj detail and DB Navigator journey detail.
