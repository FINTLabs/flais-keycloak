function Read-RequiredValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt
    )

    do {
        $value = Read-Host $Prompt

        if ([string]::IsNullOrWhiteSpace($value)) {
            Write-Host "Value is required." -ForegroundColor Yellow
        }
    } while ([string]::IsNullOrWhiteSpace($value))

    return $value
}

function Read-DefaultedValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt,

        [Parameter(Mandatory = $true)]
        [string]$DefaultValue
    )

    $value = Read-Host "$Prompt [$DefaultValue]"

    if ([string]::IsNullOrWhiteSpace($value)) {
        return $DefaultValue
    }

    return $value
}

function Read-ChoiceDefaultedValue {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt,

        [Parameter(Mandatory = $true)]
        [string]$DefaultValue,

        [Parameter(Mandatory = $true)]
        [string[]]$AllowedValues
    )

    while ($true) {
        $value = Read-DefaultedValue -Prompt $Prompt -DefaultValue $DefaultValue

        if ($AllowedValues -contains $value) {
            return $value
        }

        Write-Warning "Invalid value '$value'. Valid values: $($AllowedValues -join ', ')"
    }
}

function ConvertTo-NonEmptyStringArray {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return @(
        $Value -split "," |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Test-Yes {
    param(
        [Parameter(Mandatory = $false)]
        [string]$Value
    )

    return $Value -in @("y", "Y", "yes", "Yes", "YES")
}
