# Agent Guide

## Project
- Fabric client mod for browsing/downloading litematic schematics from a GitHub archive (see `API.md`). Legacy choculaterie/minemev endpoints are being removed in favor of the GitHub data.

## Data/API rules
- Always read `API.md` before touching network/data code.
- Images: do **not** use the `url` field from `Image`; use `path` (relative to the post folder) and lazy-load the first image per post for grids.
- Attachments: likewise use their `path` (relative) instead of any raw URL.
- Prefer lazy loading everywhere (posts, images, attachments) to keep UI responsive.

## UI/UX expectations
- Grid is an infinite-scroll gallery (no pagination); preserve scroll position when appending results.
- Channel selector is a toggleable overlay drawn over the grid: when open, dim the grid and block its interactions; keep a channel info box on the right.
- Cards are compact (about half the old size), show the first image without stretching (shrink to fit), and text scales down.
- Clicking a post opens the full detail overlay. Keep “Hide/Show Channels” clickable even when the overlay is open.

## Implementation patterns
- Use `RenderUtil.drawScaledString` instead of direct pose scaling/pushPose for scaled text.
- Keep image aspect ratios and caches; don’t reset scroll when appending posts.

## Build notes
- Gradle wrapper may need network to download; in restricted environments builds can fail on `services.gradle.org`. Use cached/offline Gradle if available.***
