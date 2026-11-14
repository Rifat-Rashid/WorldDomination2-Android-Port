# Legal soldier4 source battle oracle

Seed 0, normal difficulty, selected opponents 0 and 1. Turn 1: research soldier2 and resources. Turn 2: research soldier3 and resources. Turn 3: research soldier4 and soldier production 100%. Turn 4: soldier4 attack player 1 city index 2 and resources. Tick 0 is immediately after `btn46`; tick N follows N `BattlePlayback.step()` calls. Source playback ends at tick 363.

Generated from read-only `C:/Users/Seize/Downloads/RealTest` by `python -B analysis/suicide-source-audit/probe.py` from `D:/WorldDomination-suicide-display-depths`. The copied script and its output remain ignored under `analysis/`; no reference file was modified. The in-memory callback wrapping records city impact without changing the callback result. The copied source oracle was rerun byte-for-byte before fixture packaging.

| Fixture | Data rows | SHA-256 |
|---|---:|---|
| `source-suicide-unit.csv` | 1456 | `e69dce0e6645f466167b05cdabb3ac288c19efa0dacd55717b4e215fb11c5469` |
| `source-suicide-city.csv` | 364 | `baf13f14a3d0bfc812a2554db54490b4086445310fae80c9ab7b2f2a6a211193` |
| `source-suicide-rng.csv` | 364 | `a4eb22a38fd9c7bc2e84667d97f591e1544c9262c48f512ecd909e52d66e2e87` |
| `source-suicide-operations.csv` | 52 | `acfa199a07f51007017f8559a821a87fde26c2540a5e3b5806ea1fb009a556fb` |

Source SWF SHA-256: `fe60c4d8793a23e7ddd59ddcb406b5471540e28d64f7552d17308fbb269e6fe1`. Probe SHA-256: `dc8a9c42d685b7a8c14cb80cedbece1451d8e9f9e009781a40df636f2b82318f`.

| Read-only source | SHA-256 |
|---|---|
| `Unit.as` | `7e765e9d5bf72eb0b24b3163f0faed627cc996c4e9bd4b7075ab962d6623d1ba` |
| `View.as` | `205221608f0bb54e326cceb12f0fc92920da714b4a8daf7dce933fcd6df25f40` |
| `City.as` | `c02766d36111df366892ed494baf817ba6e89f80a11355322844da1869b83537` |
| `Presenter.as` | `d656cb3b2603d87c41a7963db933e399721c4479af046c5e0974acd6fada6ba8` |
| `movieclip_host.py` | `a489c5f3b33a1fdac4448afd07f4ca63aa32d676440dc573805ab11fb4002765` |
| `battle_playback.py` | `9aa8c82295b1977897cea2a60afbb6569e0d31e14bb9725267e422c7b9167901` |

At tick 300, source `Unit.suicide_explode` invokes `Presenter.updateSingleCity_byID(7,18)` before kill/clip replacement, causing one private battle RNG draw for dust name 98982. The new `explosion001` clip is attached at the pre-move body coordinates (206,162.2), root depth 22513; it remains stopped on frame 21 through tick 361 and detaches at 362. City 7 changes image frame 4 to 2 at tick 308, and its dust disappears with that frame replacement. Do not interpret this fixture as proof of Android pixel identity; unit city depth/pose and RNG assertions are separate from GLES composition tests.
