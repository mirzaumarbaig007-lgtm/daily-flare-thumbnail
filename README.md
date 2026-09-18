# Daily Flare Thumbnail

Android thumbnail creator for The Daily Flare.

## Included
- 3:4 portrait output (1080 x 1440)
- Fixed Daily Flare branding in the top-left
- Bottom gradient headline area
- Bottom social icons
- High-quality JPEG export to Pictures/Daily Flare
- GitHub Actions workflow that builds the APK on pushes to main and publishes the debug APK to the `apk-build-output` branch

## Recovery note
The supplied backup did not contain the canonical MainActivity.kt and pointed to commit `6858ba5c672ad4b35c6a2430cfffe5f900f4064c` in the previous repository. That previous repository is no longer accessible through the connected GitHub account, so MainActivity.kt has been reconstructed from the backup's documented feature requirements.
