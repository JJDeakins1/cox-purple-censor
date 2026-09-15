# Cox Purple Censor

A RuneLite plugin that hides Chambers of Xeric unique names until the reward chest is opened. If the loot is a new unique, it shows a collection-log popup and saves a screenshot.

## What it does

1. Censors CoX unique names in friends chat and clan broadcasts (and matching collection-log / valuable-drop lines) until you open the reward chest.
2. Hides the native collection-log popup for that unique so nobody sees the item name on-screen.
3. When the COX chest is opened:
   - Restores the real chat messages
   - Opens chat on the Game tab (so the screenshot shows the collection-log game message)
   - Shows a custom collection-log popup
   - Saves a screenshot that includes the popup

Screenshots use RuneLite's normal location and naming:

```text
.runelite/screenshots/<character>/Collection Log/
Collection log (Twisted bow) 2026-09-14_10-34-00.png
```

## Docs

- [Dev-mode Jagex login](docs/dev-mode-login.md)
- [Dev testing](docs/dev-testing.md)







