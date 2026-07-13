# LinkWeave Landing Page

The marketing landing page for [LinkWeave](https://linkweave.dev), a self-hosted
bookmark manager. Source code: <https://github.com/linkweave/linkweave>.

## Stack

- **[Astro](https://astro.build)** — static site generator, zero JS by default
- **[Tailwind CSS v4](https://tailwindcss.com)** — styling, using the LinkWeave brand palette
- **TypeScript** — type-checked via `astro check`

## Commands

```bash
pnpm install        # Install dependencies
pnpm run dev        # Dev server at http://localhost:4321
pnpm run build      # Production build to dist/
pnpm run preview    # Preview the production build
pnpm run check      # Type-check Astro components
```

## Structure

```
site/
├── public/
│   ├── logo.png              # LinkWeave wordmark
│   ├── logo-mark.png         # Icon-only logo (favicon)
│   └── screenshots/          # App screenshots captured from the running app
└── src/
    ├── layouts/
    │   └── Layout.astro      # HTML shell, meta tags, fade-in observer
    ├── components/
    │   ├── Header.astro      # Fixed nav bar
    │   ├── Hero.astro        # Headline + CTA + hero screenshot
    │   ├── Features.astro    # 12-card feature grid
    │   ├── Differentiators.astro  # Dark section: why LinkWeave vs competitors
    │   ├── Screenshots.astro # Gallery of app screenshots
    │   ├── SelfHosting.astro # Docker quick-start + operator features
    │   ├── Comparison.astro  # Feature comparison table
    │   ├── CTA.astro         # Final call-to-action
    │   └── Footer.astro
    ├── pages/
    │   └── index.astro       # Assembles all sections
    └── styles/
        └── global.css        # Tailwind import + brand tokens
```

## Updating Screenshots

The screenshots in `public/screenshots/` were captured from the running LinkWeave
dev instance. To refresh them:

1. Start the app (`cd api && ./mvnw quarkus:dev` + `cd frontend && pnpm run dev`)
2. Log in and navigate to the view you want to capture
3. Save the PNG to `public/screenshots/`
4. Reference it in `src/components/Screenshots.astro` or `Hero.astro`

## Content Accuracy

All feature claims are sourced from:
- `docs/requirements.md` — functional requirements catalog
- `docs/marketing/market-analysis.md` — competitive landscape analysis
- The actual codebase (git log, implemented features)

The comparison table data is compiled from public documentation of each
competitor as of 2026.
