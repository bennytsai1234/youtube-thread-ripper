# Thread Ripper patches (YouTube Android, Morphe)

Use Traditional Chinese with the user. Main target: the user's phone (vivo V2417A, Android 16) running YouTube 21.16.256 patched by Morphe Manager with official Morphe Patches v1.45.0, "Spoof video streams" = visionOS. The stutter only happens on the phone; desktop web YouTube is fine.

## Why this exists

- Desktop web YouTube is SABR-only (POST + UMP) and fast; there is nothing byte-range based to parallelize. The old userscript in `archive/web-userscript/` never triggered on real YouTube and is frozen. Keep it only as history.
- Spoofed non-SABR clients (visionOS, Android VR Downgraded) make the app download `/videoplayback` byte ranges. googlevideo limits each request; concurrent requests for different parts add up. This is the same situation Bilibili-thread-ripper (BTR) solves, so the patch follows BTR's idea: split a range into chunks, download concurrently, deliver strictly in order.

## Layout

- `patches/`: Kotlin bytecode patch `Multi-connection video download` (Morphe patcher). Fingerprints match media3 structure and strings, not obfuscated names.
- `extensions/youtube/`: Java runtime (`ThreadRipper` injection points, `Session` scheduler, `MediaRequest` DataSpec reader, `Config`). `extensions/youtube/stub/` holds compile-only Cronet API stubs.
- `archive/web-userscript/`: frozen v0.2.0 userscript with its own MIT license and BTR attribution.

## Hook (YouTube 21.16.256)

The UMP media data source (`alai` in 21.16.256; found by strings `/videoplayback`, `ump`, `range`) wraps media3 `CronetDataSource`. Its open/read/close get a call into `ThreadRipper`; `-2` means "not handled, run the app's code". When handled, the hook still calls BaseDataSource transferInitializing/transferStarted/bytesTransferred so the app's bandwidth meter and ABR see the real transfer. Chunk requests use the app's own `CronetEngine` (HTTP/3), GET with `&range=`; the app's own requests are POST without body.

Not handled (left native): ranges below `min_split_kib`, unknown length, SABR (`sabr=1`, request bodies), live (`sq`, `live=1`), non-googlevideo hosts. If the first response is not 2xx (e.g. 403), the range goes back to the app so its own error handling runs.

## Develop and verify

- Build: `GITHUB_ACTOR=... GITHUB_TOKEN=$(gh auth token) ./gradlew buildAndroid` (token needs `read:packages`) → `patches/build/libs/patches-*.mpp`.
- Patch locally with morphe-desktop CLI using the official `patches-1.45.0.mpp` plus ours, signed with the user's exported `Morphe.keystore` (alias and password `Morphe`, no store password), then `adb install -r`. Never commit APKs or the keystore.
- The vivo phone shows an install confirmation screen for adb installs: tell the user before installing.
- The phone runs with `log.tag=I`; debug logs are dropped, so the extension logs at info level when `log=true`.
- Runtime switch without repatching: system properties `debug.tr.enabled`, `debug.tr.threads`, `debug.tr.chunk_kib`, `debug.tr.min_split_kib`, `debug.tr.log` (`adb shell setprop debug.tr.threads 1`), read on every media request. `threads=1, chunk_kib=65536` reproduces the app's one-request-per-segment behaviour for A/B; classify results by the logged actual `threads`, not by intended mode.
- Stopping a background task in this Windows/Git Bash setup does not kill child scripts; an orphaned A/B loop kept writing settings and mislabeled rounds. Check `ps -ef` and kill leftovers before a new run.
- Verify on the phone with uncached videos (a replayed video plays from disk cache and makes no requests). Compare modes interleaved, because network capacity varies minute to minute; a single before/after pair is not evidence.
