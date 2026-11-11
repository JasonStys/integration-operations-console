# File: build-native.ps1
# Purpose: Build a genuine self-contained Windows Java application image, without an installer/service.
# State: fresh release output and ignored staging only; declaration locations: docs/code-index.md.
param(
    [string]$Output = 'release/windows',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskReleaseRoot = [IO.Path]::GetFullPath((Join-Path $taskRoot 'release'))
$taskOutput = [IO.Path]::GetFullPath((Join-Path $taskRoot $Output))
if (-not $IsWindows -or -not $taskOutput.StartsWith($taskReleaseRoot + [IO.Path]::DirectorySeparatorChar) -or
    (Test-Path -LiteralPath $taskOutput)) {
    throw 'Use a fresh output directory below release on Windows; existing packages are never removed.'
}
if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin/jpackage.exe'))) {
    throw 'Build requires a complete Java21 JDK; the finished application bundles its runtime.'
}

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build tool failed: $([IO.Path]::GetFileName($Program))" }
}

Push-Location $taskRoot
try {
    $taskFrontend = Get-Content -LiteralPath frontend/package.json -Raw | ConvertFrom-Json
    $taskVersion = $taskFrontend.version
    [xml]$taskPom = Get-Content -LiteralPath backend/pom.xml -Raw
    if ($taskPom.project.version -ne $taskVersion -or $taskVersion -ne '1.1.0') {
        throw 'Product, Maven and frontend versions must agree.'
    }
    $taskSource = (& git -c "safe.directory=$($taskRoot.Replace('\','/'))" rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $taskSource -notmatch '^[a-f0-9]{40}$') { throw 'Git source identity unavailable.' }
    $taskDirty = [bool](& git -c "safe.directory=$($taskRoot.Replace('\','/'))" status --porcelain)
    $taskJdkRelease = Get-Content -LiteralPath (Join-Path $JdkHome 'release') -Raw
    $taskJavaVersion = [regex]::Match($taskJdkRelease, '(?m)^JAVA_RUNTIME_VERSION="([0-9A-Za-z.+_-]+)"\r?$').Groups[1].Value
    if ($taskJavaVersion -notmatch '^21\.') { throw 'A verifiable Java21 runtime is required.' }
    $taskMetadata = Join-Path $taskRoot 'backend/target/desktop-metadata'
    New-Item -ItemType Directory -Path $taskMetadata -Force | Out-Null
    $taskBuildText = "version=$taskVersion`nsource=$taskSource`ndirty=$($taskDirty.ToString().ToLowerInvariant())`n"
    [IO.File]::WriteAllText((Join-Path $taskMetadata 'desktop-build.properties'), $taskBuildText,
        [Text.UTF8Encoding]::new($false))
    Invoke-Checked 'npm.cmd' @('--prefix', 'frontend', 'run', 'build')
    $taskMaven = if ($env:IOC_MAVEN) { $env:IOC_MAVEN } else { 'mvn.cmd' }
    $taskMavenArgs = @('-f', 'backend/pom.xml', '--batch-mode', '--no-transfer-progress', '-Pdesktop', '-DskipTests', 'package')
    if ($env:IOC_MAVEN_CACHE) { $taskMavenArgs += "-Dmaven.repo.local=$env:IOC_MAVEN_CACHE" }
    Invoke-Checked $taskMaven $taskMavenArgs
    $taskStaging = Join-Path $taskRoot ('.runtime/package-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path (Join-Path $taskStaging 'input') | Out-Null
    $taskJar = "integration-operations-console-$taskVersion.jar"
    Copy-Item -LiteralPath (Join-Path $taskRoot "backend/target/$taskJar") -Destination (Join-Path $taskStaging 'input')
    $taskRuntime = Join-Path $taskStaging 'runtime'
    Invoke-Checked (Join-Path $JdkHome 'bin/jlink.exe') @('--add-modules',
        'java.se,jdk.unsupported,jdk.crypto.ec,jdk.management,jdk.zipfs', '--strip-debug', '--no-header-files',
        '--no-man-pages', '--output', $taskRuntime)
    New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
    Invoke-Checked (Join-Path $JdkHome 'bin/jpackage.exe') @('--type', 'app-image', '--name',
        'IntegrationOperationsConsole', '--app-version', $taskVersion, '--vendor', 'Jason Stys',
        '--description', 'Private local synthetic integration operations console', '--input',
        (Join-Path $taskStaging 'input'), '--main-jar', $taskJar, '--main-class',
        'org.springframework.boot.loader.launch.JarLauncher', '--runtime-image', $taskRuntime,
        '--java-options', '-Dioc.desktop.launch=true', '--dest', $taskOutput)
    $taskApp = Join-Path $taskOutput 'IntegrationOperationsConsole'
    Copy-Item -LiteralPath LICENSE -Destination (Join-Path $taskApp 'LICENSE.txt')
    Copy-Item -LiteralPath docs/native-application.md -Destination (Join-Path $taskApp 'HELP.md')
    $taskLicenses = Join-Path $taskApp 'licenses'
    New-Item -ItemType Directory -Path $taskLicenses | Out-Null
    Copy-Item -LiteralPath frontend/node_modules/react/LICENSE -Destination (Join-Path $taskLicenses 'React-LICENSE.txt')
    Copy-Item -LiteralPath frontend/node_modules/react-dom/LICENSE -Destination (Join-Path $taskLicenses 'ReactDOM-LICENSE.txt')
    Copy-Item -LiteralPath docs/native-notices.md -Destination (Join-Path $taskApp 'THIRD-PARTY.md')
    [IO.File]::WriteAllText((Join-Path $taskApp 'SOURCE.json'),
        ((@{schema=1; version=$taskVersion; source=$taskSource; sourceDirty=$taskDirty;
            distribution='unsigned-windows-app-image'; javaRuntime='bundled'; javaRuntimeVersion=$taskJavaVersion} | ConvertTo-Json) + "`n"),
        [Text.UTF8Encoding]::new($false))
    Write-Output "Built IntegrationOperationsConsole $taskVersion; runtime bundled. Native acceptance is still required."
} finally {
    Pop-Location
}
