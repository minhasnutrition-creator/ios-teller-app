<#
.SYNOPSIS
    Generate a Crashlytics mapping-file id, build the release APK, and upload the
    R8 mapping file + native symbols so stack traces are deobfuscated/symbolicated.

.DESCRIPTION
    The Crashlytics Gradle plugin (com.google.firebase.crashlytics) is incompatible
    with AGP 9.0, so this script reproduces what the plugin's
    uploadCrashlyticsMappingFileRelease / uploadCrashlyticsSymbolFileRelease tasks
    would otherwise do, using the Firebase CLI instead.

    It runs the four steps in the order Crashlytics requires:
        1. firebase crashlytics:mappingfile:generateid   (writes the mapping id into
           app/src/main/res/values/crashlytics_mapping_id.xml, so it's baked into the APK)
        2. gradlew :app:assembleRelease                  (build with that id embedded)
        3. firebase crashlytics:mappingfile:upload       (deobfuscate Kotlin/Java frames)
        4. firebase crashlytics:symbols:upload           (symbolicate native .so frames)

    Run it from anywhere; it resolves the project root from its own location.

.PARAMETER DryRun
    Print the commands that would run without executing them.

.EXAMPLE
    .\scripts\crashlytics-release.ps1
    .\scripts\crashlytics-release.ps1 -DryRun

.NOTES
    Prerequisites:
      * Firebase CLI installed and logged in:  npm install -g firebase-tools  then  firebase login
      * A JDK available (the generateid step shells out to `java`); JAVA_HOME is used as a fallback.
#>
[CmdletBinding()]
param(
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

function Run {
    param([string]$Command, [string[]]$Arguments)
    Write-Host ("`n> {0} {1}" -f $Command, ($Arguments -join ' ')) -ForegroundColor Cyan
    if ($DryRun) { return }
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw ("Command failed (exit {0}): {1} {2}" -f $LASTEXITCODE, $Command, ($Arguments -join ' '))
    }
}

# ── Prerequisites ──────────────────────────────────────────────────────────
if (-not (Get-Command firebase -ErrorAction SilentlyContinue)) {
    throw 'firebase CLI not found. Install with: npm install -g firebase-tools'
}
if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'java not found on PATH or JAVA_HOME (required by the generateid step).'
}

# ── Firebase app id (from the same local.properties the app's BuildConfig uses) ──
$propsFile = Join-Path $ProjectRoot 'local.properties'
if (-not (Test-Path $propsFile)) {
    throw "local.properties not found at $propsFile"
}
$match = Select-String -Path $propsFile -Pattern '^FIREBASE_APPLICATION_ID=(.+)$'
if (-not $match) {
    throw 'FIREBASE_APPLICATION_ID not found in local.properties'
}
$appId = $match.Matches[0].Groups[1].Value.Trim()

$resourceFile = Join-Path $ProjectRoot 'app\src\main\res\values\crashlytics_mapping_id.xml'
$mappingFile  = Join-Path $ProjectRoot 'app\build\outputs\mapping\release\mapping.txt'
$libsDir      = Join-Path $ProjectRoot 'app\build\intermediates\merged_native_libs\release\mergeReleaseNativeLibs\out\lib'

# ── 1. Mapping id (must be in the APK before building) ─────────────────────
Run firebase @('crashlytics:mappingfile:generateid', '--resource-file', $resourceFile)

# ── 2. Release build ───────────────────────────────────────────────────────
Run (Join-Path $ProjectRoot 'gradlew.bat') @(':app:assembleRelease')

# ── 3. Upload mapping file (deobfuscation) ─────────────────────────────────
if (Test-Path $mappingFile) {
    Run firebase @('crashlytics:mappingfile:upload', '--app', $appId, '--resource-file', $resourceFile, $mappingFile)
} else {
    Write-Warning "Mapping file not found: $mappingFile (build may not have produced one)"
}

# ── 4. Upload native symbols (all ABIs) ────────────────────────────────────
if (Test-Path $libsDir) {
    $abiDirs = Get-ChildItem -Path $libsDir -Directory | ForEach-Object { $_.FullName }
    if ($abiDirs) {
        Run firebase (@('crashlytics:symbols:upload', '--app', $appId, '--generator', 'csym') + $abiDirs)
    } else {
        Write-Warning "No ABI directories found under $libsDir"
    }
} else {
    Write-Warning "Native libs directory not found: $libsDir"
}

Write-Host "`nDone. Deobfuscation/symbolication uploaded for app $appId" -ForegroundColor Green
