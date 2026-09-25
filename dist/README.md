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
| `keystore.properties` | release | build succeeds, APK comes out **unsigned** |
| `siren-release.jks` | release | as above |

`google-services.json` comes from the Firebase console for the
`com.research.siren` Android app. The original `siren-release.jks` was lost with
the old project folder; the current key was generated on 5 Aug 2026 by
`tools/make-keystore.ps1` — see `step.txt` section 5.

## Nothing here has been run

The checks recorded in `release/README.md` are static: they prove the code
compiled, the resources packaged and the signature is valid. They do not prove
the app launches, renders, or survives a real alert. Install on a phone and walk
Demo Mode through all three tiers.
