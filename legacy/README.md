# legacy/

Original 1.12.2 source code, kept **for reference only** while porting. Nothing in here is built.

One folder per mod, named the same as its new folder in `mods/`:

```
legacy/rubytools/      <- original 1.12.2 source (src/, build.gradle, resources...)
mods/rubytools/        <- the NeoForge 1.21.1 port
```

If you only have a `.jar` and no source, drop the jar in its folder. Jars and
`decompiled/` folders are git-ignored, so decompiled code doesn't end up committed.
