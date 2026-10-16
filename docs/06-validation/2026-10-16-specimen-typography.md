# Specimen typography alignment

**Reviewed:** 2026-10-16. **Outcome:** bounded HTML specimen typography/render review passed. This is not native application, accessibility or feature acceptance.

The change binds existing canonical roles in [preview.css](../04-design/preview.css): body14px/20px, page heading24px/32px and terminal14px/21px. Responsive terminal sizes and the narrow context heading use those same tokens. Metadata paragraphs, palette, density, geometry and canonical tokens are unchanged.

Source CSS SHA256: `7a5f92cae6e39f239955303d70fb1f80744f3584ee9fe1b99e327ed69a8b8da6`. The current source hash and all four retained image hashes were independently checked against `.cache/specimen-typography-04/report.json` while writing this record. Browser executable reports **Google Chrome155.0.8059.39**.

## Final retained renders

The integration leader visually inspected all four final screenshots: stable intended colors and readable text, with narrow-window controls visible. The measured document width equals viewport width in each capture; no document-level horizontal overflow was reported. These observations do not prove every nested region or interaction is accessible.

| Render | Viewport / document width | Body size / line | Heading size / line | Terminal size / line | Screenshot SHA256 |
| --- | --- | --- | --- | --- | --- |
| light-1440 | 1440 / 1440px | 14 / 20px | 24 / 32px | 14 / 21px | `08d862f1bd3dc374b1ddb9797ee31c32a20a1605d2435dfcbd5bc445eadbebf9` |
| dark-1440 | 1440 / 1440px | 14 / 20px | 24 / 32px | 14 / 21px | `fcc9d786b4e6d5d6f95cad51d2a53000c9c04c88d6597bf6c6a7acddfb65f636` |
| light-560 | 560 / 560px | 14 / 20px | 24 / 32px | 14 / 21px | `225ddc5a1f7577fc30ca39902f5f120d99f2c185a024078f5dd749759a947e48` |
| dark-560 | 560 / 560px | 14 / 20px | 24 / 32px | 14 / 21px | `e64d26105bf1a712e7d3bb3fb3497abbba2d458d956fb03d240c73ddf8eadac0` |

Private image paths follow `.cache/specimen-typography-04/<render>/screenshot.png`; metrics and hashes are in `report.json`. They are retained review evidence, not distributed third-party source.

## Fixture corrections and limits

Earlier attempt02 produced a duplicate light appearance in the requested dark capture because system-preference JavaScript reset the theme. Attempt03 captured the narrow dark transition. Neither is accepted as the final dark result. Final attempt04 supplies the correct dark startup preference with `--force-dark-mode`; its measured themes are light/dark as named. These were capture-fixture issues, not evidence of a repaired native app theme.

Scope is the offline **Comfortable Console synthetic specimen**, at1440px and560px widths, in light/dark appearance. It does not qualify native dp/sp equivalence, Android font scaling, physical keyboard/IME, screen reader/accessibility conformance, Desktop specimen, other density tiers, remote terminal rendering or native P1-S2 completion. No measured performance/capacity gain is claimed. No new palette, tokens, feature scope or dependency was introduced.

The [external feedback review](../04-design/2026-10-16-external-feedback-review.md) remains the original dated source assessment. This follow-up resolves only its admitted body/page/terminal typography drift; other candidates remain independently reviewed work.
