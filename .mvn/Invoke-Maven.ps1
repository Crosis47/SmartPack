# Uses Codex's managed proxy when present; otherwise runs Maven normally.
# Usage: powershell -File .mvn/Invoke-Maven.ps1 -B -ntp clean verify
$ErrorActionPreference = 'Stop'
$taskMavenArguments = @($args)
if ($taskMavenArguments.Count -eq 0) {
    $taskMavenArguments = @('clean', 'verify')
}
$taskMaven = Get-Command mvn.cmd, mvn -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $taskMaven) {
    throw 'Maven was not found on PATH.'
}
$taskRepositoryRoot = Split-Path -Parent $PSScriptRoot
$taskProxyValue = if ($env:HTTPS_PROXY) { $env:HTTPS_PROXY } else { $env:HTTP_PROXY }
$taskTemporarySettings = $null
$taskExitCode = 1
Push-Location -LiteralPath $taskRepositoryRoot
try {
    if ($taskProxyValue) {
        if ($taskMavenArguments -match '^(--settings(?:=|$)|-s$)') {
            throw 'Use the normal Maven command when passing explicit -s/--settings. This helper merges the default user settings.'
        }
        $taskProxyUri = [Uri]$taskProxyValue
        if ($taskProxyUri.Scheme -ne 'http' -or -not $taskProxyUri.Host -or $taskProxyUri.UserInfo) {
            throw 'This helper requires an HTTP proxy URL without embedded credentials.'
        }
        $taskSettings = [System.Xml.XmlDocument]::new()
        $taskUserSettings = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/settings.xml'
        if (Test-Path -LiteralPath $taskUserSettings) {
            $taskSettings.Load($taskUserSettings)
        } else {
            $taskSettings.LoadXml('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0" />')
        }
        $taskSettingsRoot = $taskSettings.DocumentElement
        $taskNamespace = $taskSettingsRoot.NamespaceURI
        $taskProxies = $taskSettingsRoot.SelectSingleNode('*[local-name()="proxies"]')
        if ($null -eq $taskProxies) {
            $taskProxies = $taskSettings.CreateElement('proxies', $taskNamespace)
            [void]$taskSettingsRoot.AppendChild($taskProxies)
        }
        # Replace proxy selection only in the temporary copy, preserving user settings.
        $taskProxies.RemoveAll()
        $taskProxy = $taskSettings.CreateElement('proxy', $taskNamespace)
        foreach ($taskEntry in @{
            id = 'codex-managed-network'
            active = 'true'
            protocol = 'http'
            host = $taskProxyUri.Host
            port = [string]$taskProxyUri.Port
            nonProxyHosts = 'localhost|127.0.0.1'
        }.GetEnumerator()) {
            $taskElement = $taskSettings.CreateElement($taskEntry.Key, $taskNamespace)
            $taskElement.InnerText = $taskEntry.Value
            [void]$taskProxy.AppendChild($taskElement)
        }
        [void]$taskProxies.AppendChild($taskProxy)
        $taskTemporarySettings = Join-Path ([System.IO.Path]::GetTempPath()) ('smartpack-maven-' + [Guid]::NewGuid().ToString('N') + '.xml')
        $taskSettings.Save($taskTemporarySettings)
        & $taskMaven.Source -s $taskTemporarySettings @taskMavenArguments
    } else {
        & $taskMaven.Source @taskMavenArguments
    }
    $taskExitCode = $LASTEXITCODE
} finally {
    if ($taskTemporarySettings -and (Test-Path -LiteralPath $taskTemporarySettings)) {
        Remove-Item -LiteralPath $taskTemporarySettings -Force
    }
    Pop-Location
}
exit $taskExitCode
