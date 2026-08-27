[CmdletBinding()]
param(
    [string]$WorkspaceRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
)

$ErrorActionPreference = 'Stop'
$workspace = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
$services = [ordered]@{
    DPOMAgent = Join-Path $workspace 'DPOMAgent'
    DPOMBaseMCPServer = Join-Path $workspace 'DPOMBaseMCPServer'
    HuaweiCloudAlarmChangeGuard = Join-Path $workspace 'HuaweiCloudAlarmChangeGuard'
    SREIntelligenceService = Join-Path $workspace 'SREIntelligenceService'
    DeepEvalService = Join-Path $workspace 'DeepEvalService'
}

$violations = [System.Collections.Generic.List[string]]::new()

function Get-ProductionFiles([string]$serviceRoot) {
    $files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    Get-ChildItem -LiteralPath $serviceRoot -Filter 'pom.xml' -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object FullName -NotMatch '[\\/](target|\.git)[\\/]' | ForEach-Object { $files.Add($_) }
    Get-ChildItem -LiteralPath $serviceRoot -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object {
            $_.FullName -Match '[\\/]src[\\/]main[\\/]' -and
            $_.FullName -NotMatch '[\\/](target|\.git)[\\/]'
        } | ForEach-Object { $files.Add($_) }
    return $files
}

function Assert-NoPattern([string]$service, [string]$label, [string]$pattern) {
    foreach ($file in Get-ProductionFiles $services[$service]) {
        $matches = Select-String -LiteralPath $file.FullName -Pattern $pattern -AllMatches -ErrorAction SilentlyContinue
        foreach ($match in $matches) {
            $relative = [System.IO.Path]::GetRelativePath($workspace, $file.FullName)
            $violations.Add("$service [$label] $relative`:$($match.LineNumber)")
        }
    }
}

function Assert-NoMavenDependency([string]$service, [string]$pattern) {
    Get-ChildItem -LiteralPath $services[$service] -Filter 'pom.xml' -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object FullName -NotMatch '[\\/](target|\.git)[\\/]' | ForEach-Object {
            [xml]$pom = Get-Content -Raw -LiteralPath $_.FullName
            foreach ($dependency in $pom.SelectNodes("//*[local-name()='dependencies']/*[local-name()='dependency']")) {
                $groupId = $dependency.SelectSingleNode("*[local-name()='groupId']")
                $artifactId = $dependency.SelectSingleNode("*[local-name()='artifactId']")
                $coordinate = "$($groupId.InnerText):$($artifactId.InnerText)"
                if ($coordinate -match $pattern) {
                    $relative = [System.IO.Path]::GetRelativePath($workspace, $_.FullName)
                    $violations.Add("$service [forbidden Maven dependency $coordinate] $relative")
                }
            }
        }
}

foreach ($entry in $services.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $entry.Value -PathType Container)) {
        $violations.Add("missing service repository: $($entry.Key)")
    }
}

if ($violations.Count -eq 0) {
    # Forbidden compile/source dependencies: services integrate through versioned contracts and transports.
    Assert-NoPattern 'DPOMBaseMCPServer' 'cross-service import' '(?m)^\s*(import|from)\s+(com\.dpom\.(agent|sre)|com\.huawei\.smartom\.changeguard)'
    Assert-NoPattern 'DPOMAgent' 'cross-service import' '(?m)^\s*(import|from)\s+(com\.dpom\.sre|com\.huawei\.smartom\.(agentic|changeguard))'
    Assert-NoPattern 'SREIntelligenceService' 'cross-service import' '(?m)^\s*(import|from)\s+(com\.dpom\.agent|com\.huawei\.smartom\.(agentic|changeguard))'
    Assert-NoPattern 'HuaweiCloudAlarmChangeGuard' 'cross-service import' '(?m)^\s*(import|from)\s+(com\.dpom\.(agent|sre)|com\.huawei\.smartom\.agentic)'
    Assert-NoPattern 'DeepEvalService' 'cross-service import' '(?m)^\s*(import|from)\s+(com\.dpom\.(agent|sre)|com\.huawei\.smartom\.(agentic|changeguard))'
    Assert-NoMavenDependency 'DPOMBaseMCPServer' '^(com\.dpom\.(agent|sre)|com\.huawei\.smartom:.*changeguard)'
    Assert-NoMavenDependency 'DPOMAgent' '^(com\.dpom\.sre|com\.huawei\.smartom:(agentic|.*changeguard))'
    Assert-NoMavenDependency 'SREIntelligenceService' '^(com\.dpom\.agent|com\.huawei\.smartom:(agentic|.*changeguard))'
    Assert-NoMavenDependency 'HuaweiCloudAlarmChangeGuard' '^(com\.dpom\.(agent|sre)|com\.huawei\.smartom:agentic)'

    # Only evidence collectors and the dedicated mutation guard may own cloud credentials.
    foreach ($service in 'DPOMAgent', 'SREIntelligenceService', 'DeepEvalService') {
        Assert-NoPattern $service 'cloud credential ownership' 'HUAWEICLOUD_(AK|SK)|CLOUD_SDK_(AK|SK)|BasicCredentials'
    }

    # Alarm-provider writes belong exclusively to HuaweiCloudAlarmChangeGuard.
    $mutationPattern = 'update-rule-disable|UpdateAlarmRuleStatus|updateAlarmRuleStatus|alarmsonoff|batchUpdateAlarm|UpdateAlarmRuleStatusRequest'
    foreach ($service in 'DPOMAgent', 'DPOMBaseMCPServer', 'SREIntelligenceService', 'DeepEvalService') {
        Assert-NoPattern $service 'alarm mutation ownership' $mutationPattern
    }

    # Distinct database aggregates must never be referenced by another service's production code/configuration.
    foreach ($service in 'DPOMBaseMCPServer', 'HuaweiCloudAlarmChangeGuard', 'SREIntelligenceService', 'DeepEvalService') {
        Assert-NoPattern $service 'DPOMAgent database ownership' '\bauthority_(investigation|tool_use|diagnosis|progress|publication|diagnostic_report)'
    }
    foreach ($service in 'DPOMAgent', 'DPOMBaseMCPServer', 'SREIntelligenceService', 'DeepEvalService') {
        Assert-NoPattern $service 'ChangeGuard database ownership' '\bguard_(operation|rule_snapshot|attempt|audit_event|outbox|idempotency)'
    }
    foreach ($service in 'DPOMAgent', 'DPOMBaseMCPServer', 'HuaweiCloudAlarmChangeGuard', 'DeepEvalService') {
        Assert-NoPattern $service 'SRE database ownership' '\b(phase[2345]_|semantic_judge_|evaluation_suite_|rule_judge_|diagnosis_event_receipt)'
    }
}

if ($violations.Count -gt 0) {
    $violations | Sort-Object | ForEach-Object { Write-Error $_ }
    throw "workspace boundary verification failed with $($violations.Count) violation(s)"
}

Write-Output 'Workspace boundary verification passed: dependencies, database ownership, cloud credentials, mutation APIs and source imports are isolated.'
