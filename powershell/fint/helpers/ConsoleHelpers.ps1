function Write-SectionTitle {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Title,

        [Parameter(Mandatory = $false)]
        [ConsoleColor]$Color = [ConsoleColor]::Cyan
    )

    Write-Host ""
    Write-Host $Title -ForegroundColor $Color
    Write-Host ('-' * $Title.Length)
}

function Write-ObjectProperties {
    param(
        [Parameter(Mandatory = $true)]
        [object]$InputObject,

        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary]$Labels
    )

    $maxLabelLength = 0
    foreach ($label in $Labels.Keys) {
        $maxLabelLength = [Math]::Max($maxLabelLength, $label.Length)
    }

    foreach ($label in $Labels.Keys) {
        $propertyName = $Labels[$label]
        Write-Host ("{0}: {1}" -f $label.PadRight($maxLabelLength), $InputObject.$propertyName)
    }
}

function Invoke-ScriptAndConvertFromJson {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $false)]
        [hashtable]$Parameters = @{}
    )

    $resultJson = & $Path @Parameters
    return $resultJson | ConvertFrom-Json
}
