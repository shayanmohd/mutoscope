# Mutoscope

Mutoscope is a finger-first, frame-by-frame loop animator for Android. Every layer is a reel with its
own number of frames, hold (ones to fours), phase offset and opacity, so a 4-frame flicker plays over a
12-frame walk and the two drift in and out of phase. Under the canvas each reel is a strip of numbered
cells on one shared time axis, and an orchid cycle marker crosses every strip where all reels realign
(the least common multiple of each reel's length times hold). Pencil, ink, marker, eraser, fill and
lasso move; onion skin; 100 steps of undo; a locked reference photo; GIF (own LZW encoder, one global
palette) and MP4 (MediaCodec) export with no watermark; `.mutoscope` project files and a one-file
backup. It is sold once on Google Play, with no ads, account or in-app purchase.

Everything runs on the device. The app declares no network permission and sends nothing anywhere.

## Build

Requires JDK 17 and the Android SDK with platform 36.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew bundleRelease       # needs keystore.properties, see below
```

`keystore.properties` and the `.jks` are not committed. Without them the release build stays
unsigned instead of failing:

```properties
storeFile=<slug>-upload.jks
storePassword=...
keyAlias=<slug>
keyPassword=...
```

## Layout

```
app/src/main/kotlin/com/mohdshayan/mutoscope/
  App.kt, MainActivity.kt   process and activity entry points
  di/                       ServiceLocator, the manual dependency container
  core/                     pure Kotlin, no Android imports, JVM-tested:
    loop/                   LoopClock (cel index, cycle LCM), canvas and export sizes
    gif/                    Lzw, GifEncoder, octree Quantizer, DelaySchedule
    video/                  Yuv (ARGB to I420 and NV12)
    paint/                  FloodFill with gap closing, StrokeSmoother, GrainNoise
    undo/                   UndoModel (100 steps)
    archive/                ProjectManifest (project.json, format 1)
    sample/                 SampleScripts, the "Lamp and ball" stroke scripts
    stats/                  DrawingDays, the opt-in local "drawing days" count
    io/                     DecodeMath for safe photo decoding
  data/prefs/               DataStore settings (AppPrefs)
  data/db/                  Room: projects, reels, cels
  data/cels/                CelStore: immutable PNG cels, write queue, proxy LruCache
  data/model/               ProjectDoc, ReelDoc, CelDoc: a loop in memory
  data/                     ProjectRepository, ArchiveRepository, ReferenceDecoder
  ink/                      Brushes, CelCompositor, InkController, InkView (touch and gestures)
  export/                   ExportManager, GifWriter, Mp4Writer, FrameRenderer, MediaSaver
  ui/theme/                 colour, type, shape and motion tokens, AppTheme
  ui/nav/                   AppNav and the type-safe routes
  ui/projects/              Projects grid and the New loop sheet
  ui/editor/                Editor, reel stack and cycle marker, tool sheets, export sheet
  ui/preview/               full-screen playback
  ui/settings/              onion defaults, backups, local counts, privacy, licences
  ui/components/            shared composables and the tool glyphs
app/src/test/               JVM unit tests
store/                      Play listing copy, icon, feature graphic, screenshots
docs/                       landing page, privacy policy and font licences (GitHub Pages)
```

## Bundled assets and licences

- Anybody (variable, `res/font/anybody_variable.ttf`), SIL Open Font License 1.1, `docs/OFL-Anybody.txt`
- Public Sans (variable, `res/font/publicsans_variable.ttf`), SIL Open Font License 1.1, `docs/OFL-PublicSans.txt`
- Material Icons (through `material-icons-extended`), Apache License 2.0
- The sample loop and the pencil grain are generated in code; no images or models ship.

## Licence

Copyright SocialSure Private Limited.
