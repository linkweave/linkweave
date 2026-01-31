# Set variables - Multiple domains separated by spaces
$domains = "local-chainlink.localhost"
# For now, use the first domain as the cert alias for backward compatibility
$cert_alias = ($domains -split ' ')[0]

# Get the project root directory (two levels up from the script's directory)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$projectRoot = Join-Path $scriptDir "..\.."

$certsDir = Join-Path $projectRoot "developer-local-settings\config\certs"

# Error handling function
function Exit-Error
{
  param ($msg)
  Write-Error "ERROR: $msg"
  exit 1
}

# Check if mkcert is installed
if (-not (Get-Command mkcert -ErrorAction SilentlyContinue))
{
  Exit-Error "Please install mkcert and run 'mkcert -install' (see: https://github.com/FiloSottile/mkcert)"
}

# Create certs directory if it doesn't exist
if (-not (Test-Path -Path $certsDir))
{
  New-Item -Path $certsDir -ItemType Directory
}

# Use a generic name for the certificate files since we're supporting multiple domains
$certFile = Join-Path $certsDir "multi-domain.pem"
$keyFile = Join-Path $certsDir "multi-domain.key"
$pkcs12File = Join-Path $certsDir "multi-domain.p12"

# Generate the keypair using mkcert
Write-Host "Generating the keypair for domains: $domains. This might take a while..."
$domainArgs = $domains -split ' '
& mkcert -cert-file $certFile -key-file $keyFile -ecdsa @domainArgs "localhost" "127.0.0.1"

# Import into local Java truststore (pkcs12 format)
Write-Host "Importing into local java truststore: $pkcs12File"

if (Test-Path $pkcs12File)
{
  Write-Host "Removing old trusted cert: $cert_alias"
  try
  {
    & keytool -delete -noprompt -v -alias $cert_alias -keystore $pkcs12File -storetype PKCS12 -storepass "changeit" | Out-Null
  }
  catch
  {
    # If the certificate does not exist, we simply ignore the error
    Write-Host "No old cert found, continuing..."
  }
}

Write-Host "Using keytool: $( (Get-Command keytool).Path )"
Write-Host "Adding new trusted cert: $cert_alias"
& keytool -importcert -noprompt -v -trustcacerts -alias $cert_alias -keystore $pkcs12File -storetype PKCS12 -storepass "changeit" -file $certFile

# Create individual certificate files for each domain (for backward compatibility)
$domainList = $domains -split ' '
foreach ($domain in $domainList) {
    $domainCertFile = Join-Path $certsDir "$domain.pem"
    $domainKeyFile = Join-Path $certsDir "$domain.key"
    Write-Host "Creating individual files for $domain"
    Copy-Item $certFile $domainCertFile
    Copy-Item $keyFile $domainKeyFile
}
