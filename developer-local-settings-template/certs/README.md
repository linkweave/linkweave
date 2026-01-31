Keys/certificates for local development

See global [README.md](../../../README.md) for more information.



add to host file Windows:(C:\Windows\System32\drivers\etc) or Linux/Mac:(/etc/hosts):
   ```
127.0.0.1 local-chainlink.localhost

   ```

Install mkcert
Make sure to install the mkcert CA to you system (browser, java keystore etc.) by running
 
   ```
   mkcert -install
   ```
Instead of running the mkcert commands below, run the "generate-keypair.sh/generate-keypair.ps1" script to generate the multi domain keypair.  
The scripts are located in the [scripts/certs](../../../scripts/certs) directory."

  ```
  # make sure you are at the right location e.g. 
  cd  developer-local-settings/config
  mkcert -key-file certs/local-chainlink.localhost.key -cert-file certs/local-esc-bs.localhost.pem local-esc-bs.localhost
  ```
