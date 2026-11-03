# UI Style Guide — Color Tokens & Theme

Defines the color system for the app: a warm cream base with a navy-blue primary accent, styled
as a modern SaaS product (generous whitespace, neutral surfaces, one accent color used sparingly
for primary actions, subtle borders over heavy shadows, soft rounded corners). This extends the
token set shadcn already generated in `frontend/src/index.css` — same variable names, same
`@theme inline` mapping pattern, just new values (plus three new semantic status tokens).

## Design intent

- **Cream, not white**, for the base surface (`--background`) — warmer and less clinical than a
  pure-white SaaS default. Cards (`--card`, `--popover`) sit a shade lighter/whiter than the page
  background so they read as distinct surfaces without needing heavy shadows.
- **Navy blue is the single accent color.** It's used for primary buttons, links, focus rings,
  active nav/sidebar items, and chart series #1 — and basically nowhere else. Resist introducing
  a second "loud" color; the restraint is what reads as modern SaaS rather than busy.
- **Text is a deep navy-charcoal**, not pure black, so headings/body text feel like part of the
  same palette as the primary accent rather than generic gray-on-white.
- **Three semantic status colors** (`success`/`warning`/`info`) are added beyond shadcn's default
  set, specifically for the derived-status badges from
  [`02-pages.md`](./02-pages.md) (`Not Geocoded` → `Geocoded` → `POIs Discovered` →
  `Routes Computed`) and any future success/error toasts:
  - `muted` (neutral gray) → **Not Geocoded** (nothing done yet)
  - `info` (sky blue, kept distinct from navy `primary` so it doesn't compete with buttons) →
    **Geocoded**
  - `warning` (amber) → **POIs Discovered** (in progress)
  - `success` (green) → **Routes Computed** (fully done)
- Radius is bumped from shadcn's default `0.625rem` to `0.75rem` — slightly softer corners, in
  line with typical modern SaaS product UI (Linear/Vercel/Ramp-style).
- Typography stays as already configured (`Geist Variable` via `@fontsource-variable/geist`) —
  no change needed there.

## Token reference (light mode)

| Token | Value | Used for |
|---|---|---|
| `--background` | `oklch(0.980 0.013 87)` — warm cream | page background |
| `--foreground` | `oklch(0.220 0.025 258)` — deep navy-charcoal | body text, headings |
| `--card` / `--popover` | `oklch(0.995 0.008 87)` — near-white warm | cards, dialogs, dropdowns |
| `--card-foreground` / `--popover-foreground` | same as `--foreground` | text on cards/popovers |
| `--primary` | `oklch(0.320 0.085 258)` — navy blue | primary buttons, links, active states |
| `--primary-foreground` | `oklch(0.985 0.008 87)` — cream-white | text/icons on navy buttons |
| `--secondary` | `oklch(0.945 0.014 87)` — soft cream-gray | secondary buttons, subtle fills |
| `--secondary-foreground` | `oklch(0.270 0.030 258)` | text on secondary fills |
| `--muted` | `oklch(0.955 0.012 87)` | muted backgrounds (table stripes, disabled) |
| `--muted-foreground` | `oklch(0.520 0.020 90)` — warm gray | secondary/help text, "Not Geocoded" badge |
| `--accent` | `oklch(0.925 0.030 250)` — pale navy tint | hover states on ghost/outline elements |
| `--accent-foreground` | `oklch(0.300 0.080 258)` | text on accent hover fills |
| `--destructive` | `oklch(0.580 0.220 27)` — red (unchanged from shadcn default) | destructive actions, error states |
| `--border` / `--input` | `oklch(0.900 0.015 87)` / `oklch(0.885 0.015 87)` | dividers, input borders |
| `--ring` | `oklch(0.550 0.095 258)` — lighter navy | focus rings (lighter than `--primary` so it's visible against navy buttons too) |
| `--success` | `oklch(0.620 0.140 155)` — green | "Routes Computed" badge, success toasts |
| `--success-foreground` | `oklch(0.985 0.008 87)` | text on success fills |
| `--warning` | `oklch(0.780 0.150 80)` — amber | "POIs Discovered" badge |
| `--warning-foreground` | `oklch(0.250 0.040 80)` | text on warning fills |
| `--info` | `oklch(0.620 0.120 240)` — sky blue | "Geocoded" badge |
| `--info-foreground` | `oklch(0.985 0.008 87)` | text on info fills |
| `--chart-1..5` | navy, teal, amber, sage, terracotta (see CSS block) | data viz series |
| `--sidebar*` | slightly deeper cream than `--background`, navy for active item | the sidebar panel on the space detail page (`03-map-and-navigation.md`) |
| `--radius` | `0.75rem` | base corner radius (buttons, cards, inputs) |

## Token reference (dark mode)

Dark mode keeps the same navy identity but inverted: a deep navy-charcoal background (not pure
black) with cream text, and the primary accent shifts to a brighter azure-blue so it stays legible
against the dark surface (a saturated navy would be nearly invisible on a navy background).

| Token | Value |
|---|---|
| `--background` | `oklch(0.170 0.022 258)` — deep navy-charcoal |
| `--foreground` | `oklch(0.960 0.015 87)` — warm cream |
| `--card` / `--popover` | `oklch(0.210 0.024 258)` — lighter navy panel |
| `--primary` | `oklch(0.720 0.100 250)` — bright azure-blue |
| `--primary-foreground` | `oklch(0.170 0.022 258)` |
| `--secondary` / `--muted` | `oklch(0.260 0.022 258)` |
| `--muted-foreground` | `oklch(0.660 0.018 87)` |
| `--accent` | `oklch(0.280 0.035 250)` |
| `--destructive` | `oklch(0.700 0.190 22)` |
| `--border` | `oklch(1 0 0 / 10%)` |
| `--input` | `oklch(1 0 0 / 15%)` |
| `--ring` | `oklch(0.720 0.100 250)` |
| `--success` | `oklch(0.700 0.150 155)` |
| `--warning` | `oklch(0.800 0.150 80)` |
| `--info` | `oklch(0.720 0.110 240)` |

## Drop-in CSS

This is the full replacement for the token blocks in `frontend/src/index.css`. It keeps the
existing structure (the `@theme inline` mapping block, `:root`, `.dark`) and adds `success`/
`warning`/`info` mappings alongside the existing `destructive` pattern — everything else
(font setup, `@custom-variant dark`, `@layer base`) stays as-is.

```css
@theme inline {
    --font-heading: var(--font-sans);
    --font-sans: 'Geist Variable', sans-serif;
    --color-sidebar-ring: var(--sidebar-ring);
    --color-sidebar-border: var(--sidebar-border);
    --color-sidebar-accent-foreground: var(--sidebar-accent-foreground);
    --color-sidebar-accent: var(--sidebar-accent);
    --color-sidebar-primary-foreground: var(--sidebar-primary-foreground);
    --color-sidebar-primary: var(--sidebar-primary);
    --color-sidebar-foreground: var(--sidebar-foreground);
    --color-sidebar: var(--sidebar);
    --color-chart-5: var(--chart-5);
    --color-chart-4: var(--chart-4);
    --color-chart-3: var(--chart-3);
    --color-chart-2: var(--chart-2);
    --color-chart-1: var(--chart-1);
    --color-ring: var(--ring);
    --color-input: var(--input);
    --color-border: var(--border);
    --color-destructive: var(--destructive);
    --color-success: var(--success);
    --color-success-foreground: var(--success-foreground);
    --color-warning: var(--warning);
    --color-warning-foreground: var(--warning-foreground);
    --color-info: var(--info);
    --color-info-foreground: var(--info-foreground);
    --color-accent-foreground: var(--accent-foreground);
    --color-accent: var(--accent);
    --color-muted-foreground: var(--muted-foreground);
    --color-muted: var(--muted);
    --color-secondary-foreground: var(--secondary-foreground);
    --color-secondary: var(--secondary);
    --color-primary-foreground: var(--primary-foreground);
    --color-primary: var(--primary);
    --color-popover-foreground: var(--popover-foreground);
    --color-popover: var(--popover);
    --color-card-foreground: var(--card-foreground);
    --color-card: var(--card);
    --color-foreground: var(--foreground);
    --color-background: var(--background);
    --radius-sm: calc(var(--radius) * 0.6);
    --radius-md: calc(var(--radius) * 0.8);
    --radius-lg: var(--radius);
    --radius-xl: calc(var(--radius) * 1.4);
    --radius-2xl: calc(var(--radius) * 1.8);
    --radius-3xl: calc(var(--radius) * 2.2);
    --radius-4xl: calc(var(--radius) * 2.6);
}

:root {
    --background: oklch(0.980 0.013 87);
    --foreground: oklch(0.220 0.025 258);
    --card: oklch(0.995 0.008 87);
    --card-foreground: oklch(0.220 0.025 258);
    --popover: oklch(0.995 0.008 87);
    --popover-foreground: oklch(0.220 0.025 258);
    --primary: oklch(0.320 0.085 258);
    --primary-foreground: oklch(0.985 0.008 87);
    --secondary: oklch(0.945 0.014 87);
    --secondary-foreground: oklch(0.270 0.030 258);
    --muted: oklch(0.955 0.012 87);
    --muted-foreground: oklch(0.520 0.020 90);
    --accent: oklch(0.925 0.030 250);
    --accent-foreground: oklch(0.300 0.080 258);
    --destructive: oklch(0.580 0.220 27);
    --success: oklch(0.620 0.140 155);
    --success-foreground: oklch(0.985 0.008 87);
    --warning: oklch(0.780 0.150 80);
    --warning-foreground: oklch(0.250 0.040 80);
    --info: oklch(0.620 0.120 240);
    --info-foreground: oklch(0.985 0.008 87);
    --border: oklch(0.900 0.015 87);
    --input: oklch(0.885 0.015 87);
    --ring: oklch(0.550 0.095 258);
    --chart-1: oklch(0.320 0.085 258);
    --chart-2: oklch(0.650 0.110 195);
    --chart-3: oklch(0.750 0.140 80);
    --chart-4: oklch(0.600 0.090 150);
    --chart-5: oklch(0.620 0.150 35);
    --radius: 0.75rem;
    --sidebar: oklch(0.965 0.013 87);
    --sidebar-foreground: oklch(0.220 0.025 258);
    --sidebar-primary: oklch(0.320 0.085 258);
    --sidebar-primary-foreground: oklch(0.985 0.008 87);
    --sidebar-accent: oklch(0.925 0.030 250);
    --sidebar-accent-foreground: oklch(0.300 0.080 258);
    --sidebar-border: oklch(0.900 0.015 87);
    --sidebar-ring: oklch(0.550 0.095 258);
}

.dark {
    --background: oklch(0.170 0.022 258);
    --foreground: oklch(0.960 0.015 87);
    --card: oklch(0.210 0.024 258);
    --card-foreground: oklch(0.960 0.015 87);
    --popover: oklch(0.210 0.024 258);
    --popover-foreground: oklch(0.960 0.015 87);
    --primary: oklch(0.720 0.100 250);
    --primary-foreground: oklch(0.170 0.022 258);
    --secondary: oklch(0.260 0.022 258);
    --secondary-foreground: oklch(0.960 0.015 87);
    --muted: oklch(0.260 0.022 258);
    --muted-foreground: oklch(0.660 0.018 87);
    --accent: oklch(0.280 0.035 250);
    --accent-foreground: oklch(0.960 0.015 87);
    --destructive: oklch(0.700 0.190 22);
    --success: oklch(0.700 0.150 155);
    --success-foreground: oklch(0.170 0.022 258);
    --warning: oklch(0.800 0.150 80);
    --warning-foreground: oklch(0.170 0.022 258);
    --info: oklch(0.720 0.110 240);
    --info-foreground: oklch(0.170 0.022 258);
    --border: oklch(1 0 0 / 10%);
    --input: oklch(1 0 0 / 15%);
    --ring: oklch(0.720 0.100 250);
    --chart-1: oklch(0.720 0.100 250);
    --chart-2: oklch(0.700 0.110 195);
    --chart-3: oklch(0.800 0.140 80);
    --chart-4: oklch(0.680 0.100 150);
    --chart-5: oklch(0.700 0.150 35);
    --sidebar: oklch(0.150 0.022 258);
    --sidebar-foreground: oklch(0.960 0.015 87);
    --sidebar-primary: oklch(0.720 0.100 250);
    --sidebar-primary-foreground: oklch(0.170 0.022 258);
    --sidebar-accent: oklch(0.280 0.035 250);
    --sidebar-accent-foreground: oklch(0.960 0.015 87);
    --sidebar-border: oklch(1 0 0 / 10%);
    --sidebar-ring: oklch(0.720 0.100 250);
}
```

## Usage map

- **Primary buttons** ("New Space", "Sign Up", "Sign In", "Compute Routes", etc.) → `--primary` /
  `--primary-foreground`. This is the only place navy should appear as a solid fill at full
  saturation — keep every other button (`secondary`, `outline`, `ghost` variants already in
  `button.tsx`) neutral.
- **Page background** → `--background` (cream). **Cards, dialogs, the "New Space" modal, dropdown
  menus** → `--card` / `--popover` (near-white), so they lift slightly off the cream page.
- **Sidebar panel** on the space detail page (`03-map-and-navigation.md`) → `--sidebar*` tokens; the
  active/selected POI row uses `--sidebar-accent`.
- **Status badges** on the Spaces page (`02-pages.md`'s derived-status pipeline) →
  `muted` (Not Geocoded) / `info` (Geocoded) / `warning` (POIs Discovered) / `success` (Routes
  Computed).
- **Focus rings / keyboard navigation** → `--ring` everywhere (already wired into shadcn's base
  component styles via `focus-visible:ring-ring/50`).
- **Charts** (if a travel-time comparison chart is ever added to the space detail page) →
  `--chart-1` through `--chart-5` in order, so the primary series always matches the navy brand
  color.

## Applying this

This doc is the spec; applying it means replacing the `@theme inline`, `:root`, and `.dark` blocks
in `frontend/src/index.css` with the CSS block above (font setup and `@layer base` rules stay
unchanged). No component code needs to change — every shadcn component already reads these same
variable names (see `frontend/src/components/ui/button.tsx`'s use of `bg-primary`,
`text-primary-foreground`, etc.), so swapping the token values re-themes the whole app at once.
