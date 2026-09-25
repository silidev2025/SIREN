# Built APKs

```
dist/
  release/   signed, minified — what gets installed for a drill or the defence
  debug/     unminified and debuggable — what gets used while testing
```

Each folder has its own README with the build command, where the artifact lands
and what to check before trusting it.

`package com.research.siren` · `compileSdk 37` · `targetSdk 35` · `minSdk 24`

Both variants share the application id, so they **cannot be installed side by
side** and their signing keys differ — uninstall before switching.

## A fresh clone cannot build either of these

Three files are gitignored on purpose and have to be restored first:

| File | Needed for | If missing |
|---|---|---|
| `app/google-services.json` | any build | the `google-services` plugin fails the build outright |
| `env.local` (template: `env.local.example`) | release | build succeeds, APK comes out **unsigned** |
| `siren-release.jks` | release | as above |

`google-services.json` comes from the Firebase console for the
`com.research.siren` Android app, or can be rebuilt from any APK here — CLAUDE.md,
*Secrets*, has both routes. The release key has been lost and regenerated more than
once and there are currently two candidates; read CLAUDE.md, *The release signing key*,
before signing anything. (`tools/make-keystore.ps1`, which an earlier version of this file
named, never existed.)

## Nothing here has been run

The checks recorded in `release/README.md` are static: they prove the code
compiled, the resources packaged and the signature is valid. They do not prove
the app launches, renders, or survives a real alert. Install on a phone and walk
Demo Mode through all three tiers.
