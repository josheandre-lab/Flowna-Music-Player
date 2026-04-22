param(
    [Parameter(Mandatory = $true)]
    [string]$Message,

    [string]$Label,

    [switch]$Push
)

$ErrorActionPreference = "Stop"

$repoRoot = (git rev-parse --show-toplevel).Trim()
if (-not $repoRoot) {
    throw "Git repository could not be resolved."
}

Push-Location $repoRoot
try {
    $branch = (git branch --show-current).Trim()
    if (-not $branch) {
        throw "Current branch could not be resolved."
    }

    git add --all
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git add --all"
    }

    $stagedFiles = git diff --cached --name-only
    $hasStagedFiles = -not [string]::IsNullOrWhiteSpace(($stagedFiles | Out-String))

    if ($hasStagedFiles) {
        git commit -m $Message
        if ($LASTEXITCODE -ne 0) {
            throw "Git command failed: git commit -m $Message"
        }
    }

    if ([string]::IsNullOrWhiteSpace($Label)) {
        $Label = $Message
    }

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $slug = ($Label.ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
    if ([string]::IsNullOrWhiteSpace($slug)) {
        $slug = "snapshot"
    }

    $tagName = "restore-$timestamp-$slug"
    git tag -a $tagName -m "Restore point: $Message"
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git tag -a $tagName"
    }

    if ($Push) {
        git push origin $branch
        if ($LASTEXITCODE -ne 0) {
            throw "Git command failed: git push origin $branch"
        }

        git push origin $tagName
        if ($LASTEXITCODE -ne 0) {
            throw "Git command failed: git push origin $tagName"
        }
    }

    Write-Host ""
    Write-Host "Restore point hazir."
    Write-Host "Branch : $branch"
    Write-Host "Tag    : $tagName"
}
finally {
    Pop-Location
}
