# Source and dependency notes

- `api-102/` contains libxposed API 102 sources from the JingMatrix/libxposed-api fork. Its Apache 2.0 license is retained at `api-102/LICENSE`. These interfaces are compile-only dependencies of the LSPosed module.
- `liquidglass/` is the UI library snapshot already used by this project. Existing package names and source notices are retained. Its Gradle file lists the Compose, Backdrop and Shapes dependencies used by the settings screen.
- The direct APK builder uses smali/dexlib2 2.5.2 and its dependencies from Maven Central. The versions and SHA-256 checksums are pinned in `direct/tooling-lock.json`. The downloaded tool JARs are local build dependencies and are not committed or included in the generated APK.
- Reference APKs and decompiled vendor application sources are not included in this repository. The direct builder requires the user to supply the verified original WeType APK. Existing shader extraction/provenance tooling is retained under `tools/`.

This file records provenance; it does not grant rights to third-party code or application packages beyond their original terms.
