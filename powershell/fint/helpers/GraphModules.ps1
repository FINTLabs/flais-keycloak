& {
    $requiredVersion = [version]'2.39.0'
    $moduleNames = @(
        'Microsoft.Graph.Authentication'
        'Microsoft.Graph.Applications'
    )

    $conflictingModules = @(Get-Module -Name 'Microsoft.Graph*' | Where-Object {
        $_.Version -ne $requiredVersion
    })
    if ($conflictingModules.Count -gt 0) {
        $details = ($conflictingModules | ForEach-Object { "$($_.Name) $($_.Version)" }) -join ', '
        throw "Microsoft Graph $requiredVersion is required, but other versions are loaded: $details. Open a fresh PowerShell session and run the script again."
    }

    foreach ($moduleName in $moduleNames) {
        $installed = @(Get-Module -ListAvailable -Name $moduleName | Where-Object {
            $_.Version -eq $requiredVersion
        })

        if ($installed.Count -eq 0) {
            Write-Host "Installing $moduleName $requiredVersion for the current user..." -ForegroundColor Cyan
            Install-Module -Name $moduleName -RequiredVersion $requiredVersion `
                -Scope CurrentUser -Repository PSGallery -Force -ErrorAction Stop
        }

        Import-Module -Name $moduleName -RequiredVersion $requiredVersion -Global -ErrorAction Stop
    }
}
