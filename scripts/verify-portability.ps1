[CmdletBinding()]
param(
    [string] $RootPath = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = if ([string]::IsNullOrWhiteSpace($RootPath)) {
    (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
} else {
    (Resolve-Path -LiteralPath $RootPath).Path
}
$violations = [System.Collections.Generic.List[string]]::new()

function Test-PortableFile {
    param(
        [Parameter(Mandatory)] [string] $Path,
        [Parameter(Mandatory)] [string[]] $Patterns
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    $relative = [IO.Path]::GetRelativePath($repositoryRoot, $Path)
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadLines($Path)) {
        $lineNumber++
        foreach ($pattern in $Patterns) {
            if ($line -match $pattern) {
                $violations.Add("${relative}:${lineNumber}: $pattern")
            }
        }
    }
}

$machinePath = '(?i)[A-Z]:\\(?:code)(?:\\|\b)'
$parentContract = '(?i)(?:\.\.[\\/])+(?:contracts)(?:[\\/]|\b)'

$authoritativeDocs = [System.Collections.Generic.List[string]]@(
    (Join-Path $repositoryRoot 'AGENTS.md'),
    (Join-Path $repositoryRoot 'README.md'),
    (Join-Path $repositoryRoot 'docs\platform\ADR.md'),
    (Join-Path $repositoryRoot 'docs\platform\README.md'),
    (Join-Path $repositoryRoot 'docs\platform\MIGRATION.md')
)
$phaseDirectory = Join-Path $repositoryRoot 'docs\platform\phases'
if (Test-Path -LiteralPath $phaseDirectory -PathType Container) {
    Get-ChildItem -LiteralPath $phaseDirectory -Filter '*.md' -File |
        ForEach-Object { $authoritativeDocs.Add($_.FullName) }
}

$activeBuildInputs = @(Get-ChildItem -LiteralPath $repositoryRoot -File -Recurse |
    Where-Object {
        $_.FullName -notmatch '[\\/](?:target|\.git)[\\/]' -and
        ($_.Name -eq 'pom.xml' -or $_.Extension -in @('.ps1', '.py'))
    } | Select-Object -ExpandProperty FullName)

$activeContractDocs = @()
$contractDirectory = Join-Path $repositoryRoot 'contracts'
if (Test-Path -LiteralPath $contractDirectory -PathType Container) {
    $activeContractDocs = @(Get-ChildItem -LiteralPath $contractDirectory -File -Recurse |
        Where-Object { $_.Extension -eq '.md' } | Select-Object -ExpandProperty FullName)
}

foreach ($path in @($authoritativeDocs + $activeBuildInputs + $activeContractDocs | Sort-Object -Unique)) {
    Test-PortableFile -Path $path -Patterns @($machinePath, $parentContract)
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error $_ }
    throw "PORTABILITY_CHECK_FAILED: $($violations.Count) violation(s)"
}

Write-Output "PORTABILITY_CHECK_PASSED"
