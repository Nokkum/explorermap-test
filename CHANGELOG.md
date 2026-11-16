# Changelog

## Unreleased (2.2.1+26.3)

### Security
- Capacity packet checks distance before reading the block.
- Expansion needs a nearby cartography table, has a 1 s cooldown, and the map family is capped.
- Discovery uploads only count pixels near the player.
- System waypoints are only sent for pixels the player has discovered.
- Rejected-packet logs are throttled and sanitized.
- Waypoint bounds match the tile size (64 x scale blocks).
- Rejected waypoint edits, deletes and shares resync the client, so no ghost waypoints.
- Server reads `config/explorermap-server.json` (paper cost), not the client Cloth config.
- Cloth Config is no longer required on dedicated servers.
- Server accepts any well-formed waypoint icon id.

### Waypoints
- Slots and share charges are per player and per map; old shared pools move to current waypoint owners.
- Slots count your waypoints across all expansion tiles.
- Re-sharing a waypoint no longer costs a charge.
- Ops can edit and delete system waypoints.
- The Waypoints x/y counter shows your own waypoints, matching the server.
- Edit screen keeps custom colors; name limit is now 64.
- Sync keeps your own waypoints first when over the limit.
- Waypoints without an id get a stable id across loads.
- Unknown icons are no longer dropped on load.

### Structures
- Fixed structure ids (`village_*`, `mineshaft_mesa`, `mansion`); removed `dungeon` and `jungle_temple`.
- Large structures seen from the side are detected.
- Underground structures no longer get waypoints from the ground above.
- Duplicate check only compares automatic waypoints of the same type.
- Pending-chunk list is saved on change and raised to 16,384.
- Automatic waypoint names are translated per player.

### Maps and expansion
- Expansion tiles now render: the server paints and syncs their colors.
- Cartography table opens with either hand when a map is in the off-hand.
- Framed maps sync discovery and waypoints.
- HD toggle keeps the viewed framed map; framed maps are view-only.
- Shared discovery now reaches the recipient even if others found it first.

### UI
- Toolbar wraps at high GUI scales; cartography panel fits the window.
- Minimap arrows and dimension label stay on screen with small padding.
- Cartography table shows failure messages and refreshes its buttons.
- Expand buttons need a table in reach.
- Removed minimap click-to-open (the cursor is grabbed in-game).
- Translated hard-coded labels.

### Discovery and performance
- Upload interval is real time (3 s) instead of discovery ticks.
- Rays use the vertical FOV and window aspect, with jitter to fill gaps and fewer rays per tick.
- Hit points use the block center; world-to-pixel uses floor.
- Tile grid, canvas and tile lookup are cached.
- Recency fade repaints every 4th tick.
- Biome color lookup is skipped when no provider is registered.
- With fog off, only pixels in range are marked.

### Data and config
- Discovery bitmasks are saved as byte arrays (old saves still load).
- Skipped invalid entries are logged as warnings.
- Config values are clamped on load; added tooltips; removed unused lang keys.
- `expansionPaperCost` moved to the server config.

### Build and cleanup
- Loom pinned to 1.18.4.
- Added unit tests (limiter, tracker, structure ids) and a GitHub Actions build.
- Removed dead code, unused imports, redundant `server.execute()` hops and the redundant use-key loop.
