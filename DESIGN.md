# Design

## Source of truth
- Status: Active
- Last refreshed: 2026-08-04
- Primary product surfaces: Android phone music and audiobook libraries, library/detail screens, Now Playing, queue/playback controls, downloads, settings, and the classic iPod touch shell.
- Evidence reviewed: `README.md`; `Github/Images/*.png`; `.omx/ultraqa/*.png`; the local read-only `zzanehip/The-OldOS-Project` iPod implementation obtained with GitHub CLI; `app/src/main/java/com/craftworks/music/MainActivity.kt`; `app/src/main/java/com/craftworks/music/NavGraph.kt`; `app/src/main/java/com/craftworks/music/ui/theme/*`; `app/src/main/java/com/craftworks/music/ui/screens/*`; `app/src/main/java/com/craftworks/music/ui/playing/*`; `app/src/main/java/com/craftworks/music/ui/elements/*`; `app/src/main/java/com/craftworks/music/managers/settings/AppearanceSettingsManager.kt`.
- Replica baseline: the first-generation iPod touch Music player on a 320 x 480 point canvas. The OldOS iOS 4 recreation is a measurement crib for the classic controls and Helvetica Neue metrics that remained consistent with the early player; first-generation proportions and the user's supplied direction take precedence where eras differ.

## Brand
- Personality: Chora's normal mode is expressive, modern, and customizable. iPod touch mode is familiar, focused, tactile, and deliberately period-authentic.
- Trust signals: real library data, truthful playback state, immediate mode switching, legible metadata, deterministic controls, and no decorative state that implies nonexistent behavior.
- Avoid: applying an iOS color skin to Material components; modern rounded cards; oversized type; floating controls; dynamic-color leakage; fake static library data; copied Apple image assets; and mixing Material and classic iOS chrome in the same mode.

## Product goals
- Goals: provide a switchable interface mode that recreates the complete classic iPod touch Music experience while using Chora's existing Navidrome/local library, queue, and Media3 playback engine.
- Goals: cover the primary Playlists, Artists, Songs, Albums, Audiobooks, More, detail, and Now Playing flows with consistent classic chrome and interaction behavior.
- Goals: preserve the existing Chora interface unchanged when classic mode is disabled.
- Goals: make audiobooks a first-class, download-first library type with dedicated discovery, chapter browsing, exact resume, variable speed, sleep timer, offline listening, and the same live playback state in both Chora and classic shells.
- Goals: keep music and audiobook browsing intentionally separate while sharing one queue, one Media3 controller, and one local/server progress contract.
- Non-goals: emulate iOS itself outside the app; paste reference screenshots or Apple bitmap chrome into the UI; replace Chora's playback/data architecture; or remove Chora-only features.
- Success signals: every primary screen renders without Material chrome in classic mode; all lists use live data; play, pause, seek, previous, next, shuffle, repeat, and queue behavior affect the real controller; Android back navigation is coherent; and the mode survives process restart.

## Personas and jobs
- Primary personas: listeners nostalgic for the classic iPod touch; users who want a compact, information-dense music library; audiobook listeners returning across long sessions and devices; and Chora developers testing alternate shells against the same playback core.
- User jobs: browse a large library quickly; drill from artist or album to songs; continue a book at the exact saved point; choose chapters, speed, seek interval, and sleep timing; download a whole book or an individual part; start playback without losing the existing queue contract; see exactly what is playing; control playback and seek; and return to normal Chora settings.
- Key contexts of use: portrait phones operated by touch, offline or cached libraries, active playback, empty first-sync state, and intermittent server connectivity.

## Information architecture
- Primary navigation: six fixed tabs in this order: Playlists, Artists, Songs, Albums, Books, More. `Books` is the compact tab label for the full Audiobooks library; every label uses the same tab type token and must remain on one line without overlapping adjacent tabs.
- Core routes/screens: tab roots; playlist detail; artist detail; album detail; Now Playing; More; Chora Settings handoff.
- Normal Chora navigation treats Audiobooks as a primary configurable destination and gives it a prominent Home entry. Its root contains Continue Listening, Downloaded, Finished, and All Books; book detail contains resume/play actions, parts, embedded chapters, download state, and progress.
- Content hierarchy: system/status area, 44-point navigation bar, primary scroll content, a persistent 54-point classic playback strip whenever audio is loaded, and a 49-point tab bar. Detail screens replace the tab title and expose a period-style back button. Now Playing becomes a full-screen destination above the tab shell.
- More contains Chora-specific destinations that do not fit the primary hierarchy: Radio, Queue, Downloads, Settings, and Return to Chora. Audiobooks is not duplicated here because it owns a first-class bottom tab.

## Design principles
- Replica before reinterpretation: use classic iOS proportions, density, hierarchy, dividers, gradients, and chrome instead of translating them into Material equivalents.
- Real app, not mockup: every visible list, selection, control, and playback state is connected to existing Chora data and controller behavior.
- One listening state: progress, current item, queue mutations, speed, and completion remain coherent when moving between normal Chora, classic mode, offline playback, and another Subsonic client.
- Content boundaries are explicit: a server library is typed `music` or `audiobook`; music screens exclude audiobook content and audiobook screens exclude music content. Name and `.m4b` inference exist only as compatibility fallbacks for untyped servers or legacy data.
- Isolation: classic tokens and components live in the iPod mode package; existing Chora theme/components remain the default mode and should not gain replica-specific branches.
- Escape hatch: the user can always reach Settings or use Return to Chora from More; changing mode never requires restarting the app.
- Tradeoffs: platform-native Android system bars remain visible and accessible, but their colors/icons are coordinated with the replica. Apple proprietary artwork and fonts are recreated with Compose primitives, gradients, text, and existing open vector assets rather than copied.

## Visual language
- Color: white content (`#FFFFFF`); primary text (`#111111`); secondary text (`#6A6A6A`); separators (`#C7C7C7`); classic blue (`#2E72B8`); selected blue (`#4AA3E8`); navigation chrome gradient (`#F4F4F4` to `#A8A8A8`); dark tab gradient (`#383838` to `#090909`); dark Now Playing chrome (`#3B3B3B` to `#111111`). No dynamic color in replica mode.
- Typography: bundled Helvetica Neue; 22sp Bold navigation titles, 18sp Bold list/song titles, 14sp Regular subtitles, 13sp Bold Now Playing metadata, 14sp Bold playback times, and 11sp Bold tab labels. Song-title tracking is slightly tightened rather than widened. Avoid Material typography roles inside replica components.
- Spacing/layout rhythm: model the 320 x 480 point first-generation reference directly in density-independent units. Status insets are additive; compact rows are 44dp and two-line song rows are 52dp; nav bar is 44dp; tab bar is 49dp; mini playback strip is 54dp; primary horizontal inset is 12dp; separator inset is 12dp unless imagery establishes a 54dp text column.
- Shape/radius/elevation: square list and bar edges; 5dp button radii only for classic beveled buttons; 4dp artwork rounding at most; 1px/dp separators; shadow and highlight are baked into gradients rather than Material elevation.
- Motion: short 180-250ms horizontal drill-in/drill-out transitions; immediate tab replacement; modest pressed-state darkening; no ambient animation. Building a generated mix is the deliberate exception: while generation is active, the complete visible app surface performs an exaggerated perspective tumble with depth scaling and wobble, then snaps back into place when the result arrives.
- Imagery/iconography: square cover art with crop (`ContentScale.Crop`); compact monochrome tab/control glyphs drawn from Compose or project vectors; generated fallback art remains deterministic per album. Decorative Apple logos and product marks are excluded.

## Components
- Existing components to reuse: Media3 `MediaController`; repository-backed screen view models; Coil image loading; generated artwork fallback; current settings screens; current queue mutations and playback helpers.
- New/changed components: `IpodTouchApp`, classic system/nav/tab/playback bars, persistent mini-player, classic list row with long-press actions, album grid cell, artwork view, route stack, six-tab definitions including a direct Audiobooks root, library/detail screens, More screen, full-screen classic Now Playing, classic playlist picker/action sheet, audiobook library and book-detail surfaces, continue-listening cards, chapter rows, progress indicators, and audiobook transport/speed controls.
- Variants and states: selected/unselected/pressed tab; root/detail navigation bar; image/loading/fallback artwork; playing/paused controls; repeat off/all/one; populated/empty/loading/error list; narrow/wide layout.
- Token/component ownership: all replica colors, dimensions, gradients, shapes, typography, icon treatment, and semantics are owned by `ui/ipod`. Interface-mode persistence is owned by `AppearanceSettingsManager`. The app-shell switch is owned by `MainActivity`.

## Accessibility
- Target standard: WCAG 2.1 AA where compatible with faithful visual reproduction; never sacrifice readable contrast or functional semantics for decoration.
- Keyboard/focus behavior: tab items, list rows, navigation buttons, and playback controls expose ordered focus targets and activate through Compose click semantics; Android back unwinds detail/Now Playing before leaving the app.
- Contrast/readability: primary text at least 4.5:1; secondary text at least 3:1 for large labels and 4.5:1 otherwise; controls remain distinguishable without color alone.
- Screen-reader semantics: artwork names the associated album; controls expose action and current state; selected tab uses selected semantics; time sliders expose current and total duration; decorative gradients/dividers are silent.
- Sensory considerations: ordinary navigation remains brief and nonessential. The user explicitly opts into the intentionally intense generated-mix tumble by pressing Build; it never flashes, obscures a completed result, or continues after generation ends.

## Responsive behavior
- Supported breakpoints/devices: Android phones from 320dp compact portrait through large portrait and landscape; the 320 x 480 point portrait canvas is the fidelity baseline.
- Layout adaptations: bars retain fixed classic heights; on tall portrait screens, Now Playing centers the square uncropped artwork within the media region, fills the balanced surplus above and below with inverted reflections fading into black, keeps timing/shuffle/repeat controls fixed immediately beneath the metadata instead of moving with the cover, and anchors compact transport/volume chrome to the bottom; album grids choose two columns below 600dp and additional fixed-width columns above it; large/landscape displays center a maximum-width 600dp classic canvas with neutral side gutters instead of stretching proportions.
- Touch/hover differences: visible glyphs may be smaller for fidelity, but interactive hit targets remain at least 44 x 44dp. TV/D-pad receives focus rings without changing the baseline phone appearance.

## Interaction states
- Loading: preserve the classic screen structure and show a centered small progress indicator plus concrete status text; never display a fake 0% progress value.
- Empty: show a plain centered title and one-line explanation inside the white content area, with an actionable Settings entry in More.
- Error: show a compact inline error row and a Retry button; keep cached content visible when available.
- Success: content replaces loading state without a success toast for normal library loads.
- Disabled: retain layout, reduce opacity, and expose the unavailable state to accessibility services.
- Offline/slow network, if applicable: cached Room content remains browsable; active sync status is described in More rather than obscuring the library with a modal or indefinite overlay.
- Audiobook progress: save locally at regular playback checkpoints and immediately on pause, seek, item transition, completion, and service shutdown. Upload a debounced server bookmark, retain a durable constrained retry across process death/reboot, and reconcile by newest timestamp when reconnecting; never regress a newer local or remote position.
- Audiobook offline policy: starting or resuming any book queues the complete book for device storage, prioritizing the selected/current part, while playback may begin immediately. Downloaded files always win over remote streams. The detail UI identifies whole-book and per-part offline state and makes download-first behavior explicit.
- Resume/completion: Resume starts the saved part and millisecond offset. A book is finished at 95% or with no more than 60 seconds remaining; replaying or explicitly restarting can clear completion. Progress UI always identifies the book/part and never reports a fabricated percentage.
- Audiobook transport: provide 15-second back, 30-second forward, chapter previous/next, speed, and sleep timer without changing music controls. Tapping a chapter starts at its real timestamp; tapping a book resumes it. Existing play-now and add-to-queue-top/bottom semantics remain intact.

## Content voice
- Tone: terse, neutral, and period-appropriate inside replica mode.
- Terminology: use Songs, Albums, Artists, Playlists, More, Now Playing, Shuffle, Repeat, and Settings. Preserve server-provided metadata verbatim except existing track-number formatting preferences.
- Microcopy rules: titles are short; empty/error states explain the next useful action; no promotional language or Material-era labels inside classic chrome. Normal Chora mode may use curated rotating headings that stay stable while a screen is open. Avoid product self-congratulation, em dashes, and contrast templates shaped like “it is not X, it is Y” in both modes.

## Implementation constraints
- Framework/styling system: Kotlin, Jetpack Compose, Hilt view models, DataStore preferences, Media3, Coil, and existing project resources. No new dependency is required.
- Design-token constraints: replica values are explicit immutable tokens under `ui/ipod`; do not read `MaterialTheme.colorScheme` for replica rendering and do not change the normal Chora theme.
- Performance constraints: lazy lists/grids for library content; stable keys where IDs exist; no per-frame bitmap generation; no synchronous network access from composables.
- Compatibility constraints: onboarding always uses the normal Chora shell; classic mode is applied after onboarding. System bars remain Android-owned. Existing queue preservation and top/bottom insertion semantics must remain intact.
- Audiobook data contract: albums are books; media files are parts/chapters; a single M4B may additionally expose embedded chapter markers. Library classification and chapter metadata come from OpenSubsonic-compatible response extensions when present and fall back safely when absent. Resume position uses the existing per-user Subsonic bookmark position in milliseconds.
- Persistence constraints: Room is the offline-first authority for pending progress. Progress writes must not block playback or composables, and bookmark sync must be bounded/debounced rather than tied to frame-level position updates. Playback speed is restored per book.
- Test/screenshot expectations: pure tests cover preference parsing/tab and route contracts; targeted unit tests and `assembleDebug` must pass; lint must introduce no new errors; the USB DUT is the final visual and interaction check at its actual density. Capture at least the Songs root, Albums root, a detail screen, More, and Now Playing.
- Acceptance criteria: enabling iPod touch mode changes the complete app shell immediately; disabling it restores Chora immediately; all six tabs work and Audiobooks opens directly from its bottom tab; Playlists/Artists/Albums/Audiobooks drill down; tapping any song preserves the logical queue, begins playback, and opens Now Playing; long-pressing any song opens the shared playlist/queue/Instant Mix/download actions; the full logical queue and controller state update live in both Chora and iPod shells; a persistent classic mini-player appears directly above the tab bar; Now Playing reports and controls the real state; no tab labels overlap; no cover is stretched; no Material navigation/card treatment leaks into the replica; cached content remains usable without network.
- Audiobook acceptance criteria: typed audiobook libraries never pollute music Albums/Artists/Songs/Home; Audiobooks exposes Continue Listening, Downloaded, Finished, and All Books; a book with multiple files and a single M4B with embedded markers both show ordered chapters; starting or resuming automatically queues the full book locally with the selected part first; downloaded content plays with no server path; playback resumes to the saved millisecond offline and after reconnecting; pending bookmarks survive app/process death and upload once a network returns; bookmarks round-trip through Navidrome; progress never moves backward during reconciliation; speed, seek, chapter navigation, sleep timer, whole-book/part downloads, and completion work; standard and iPod shells reflect the same state immediately; no loading surface can remain at unexplained `0%`.

## Open questions
- [ ] Confirm whether landscape should reproduce the original Cover Flow transition or retain the centered portrait canvas. Owner: product. Impact: affects large-screen presentation only.
