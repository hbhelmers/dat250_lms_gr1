function Find-LmsJavaHome {
    [CmdletBinding()]
    param(
        [string] $JavaHome = $env:JAVA_HOME,
        [string] $ExecutablePath = $env:PATH,
        [string[]] $InstallRoots = @(
            "$env:ProgramFiles\Java",
            "$env:ProgramFiles\Eclipse Adoptium",
            "$env:ProgramFiles\Microsoft",
            "$env:ProgramFiles\Amazon Corretto",
            "$env:ProgramFiles\Zulu",
            "$env:ProgramFiles\BellSoft",
            "$env:USERPROFILE\.jdks"
        )
    )

    function Get-CompatibleJdk([string] $Directory) {
        if ([string]::IsNullOrWhiteSpace($Directory)) { return }
        $Directory = $Directory.Trim().Trim('"')
        foreach ($file in @('bin\java.exe', 'bin\javac.exe', 'release')) {
            if (-not (Test-Path -LiteralPath "$Directory\$file" -PathType Leaf)) { return }
        }

        # Read JDK metadata without executing PATH shims or an incompatible JVM.
        $release = Get-Content -Raw -LiteralPath "$Directory\release" -ErrorAction SilentlyContinue
        $version = [regex]::Match([string] $release, '(?m)^JAVA_VERSION="(\d+)(?:[.\-+"])')
        if (-not $version.Success) { return }
        $major = [int] $version.Groups[1].Value

        # Java 21 is the pom.xml target; Spring Boot 4.1 supports JDKs through 26.
        if ($major -lt 21 -or $major -gt 26) { return }
        [pscustomobject] @{
            Directory = (Get-Item -LiteralPath $Directory).FullName
            Major = $major
        }
    }

    $jdk = Get-CompatibleJdk $JavaHome
    if ($jdk) { return $jdk.Directory }

    foreach ($directory in ($ExecutablePath -split ';')) {
        if ([string]::IsNullOrWhiteSpace($directory)) { continue }
        $directory = $directory.Trim().Trim('"')
        foreach ($name in @('javac.exe', 'java.exe')) {
            $executable = Get-Item -LiteralPath "$directory\$name" -ErrorAction SilentlyContinue
            if (-not $executable) { continue }
            $jdk = Get-CompatibleJdk (Split-Path (Split-Path $executable.FullName -Parent) -Parent)
            if ($jdk) { return $jdk.Directory }

            # Also follow executable symlinks when PATH points to a shim folder.
            foreach ($target in $executable.Target) {
                if (-not [System.IO.Path]::IsPathRooted($target)) {
                    $target = Join-Path $directory $target
                }
                $jdk = Get-CompatibleJdk (Split-Path (Split-Path $target -Parent) -Parent)
                if ($jdk) { return $jdk.Directory }
            }
        }
    }

    $installed = foreach ($root in $InstallRoots) {
        if ([string]::IsNullOrWhiteSpace($root)) { continue }
        Get-CompatibleJdk $root
        foreach ($directory in (Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue)) {
            Get-CompatibleJdk $directory.FullName
        }
    }
    $jdk = $installed | Sort-Object Major -Descending | Select-Object -First 1
    if ($jdk) { return $jdk.Directory }

    throw 'No compatible JDK found. Install JDK 21-26 and reopen the terminal. The wrapper searches JAVA_HOME, PATH and standard JDK installation folders.'
}
