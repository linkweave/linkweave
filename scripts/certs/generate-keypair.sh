#!/bin/bash
set -Eeuo pipefail

# Multiple domains separated by spaces
domains="local-chainlink.localhost"
# For now, use the first domain as the cert alias for backward compatibility
cert_alias=$(echo ${domains} | cut -d' ' -f1)

project_root=$(dirname "$0")/../..
certs_dir="${project_root}/developer-local-settings/config/certs"

function exit_error {
	local msg="$*"
	echo "ERROR: ${msg}" >&2
	exit 1
}

which mkcert || exit_error "Please install mkcert and run 'mkcert -install' (see: https://github.com/FiloSottile/mkcert)"

if [ ! -d "${certs_dir}" ]; then
	mkdir -p "${certs_dir}"
fi

# Use a generic name for the certificate files since we're supporting multiple domains
cert_file="${certs_dir}/multi-domain.pem"
key_file="${certs_dir}/multi-domain.key"
pkcs12_file="${certs_dir}/multi-domain.p12"

echo "Generating the keypair for domains: ${domains}. This might take a while..."
# Also generate for localhost/127.0.0.1 to make Quarkus Dev-UI work properly using https://localhost:8443/q/dev-ui
# should also add the cert to the global java cacert store if JAVA_HOME setup correctly
mkcert \
	-cert-file "${cert_file}" \
	-key-file "${key_file}" \
	-ecdsa \
	${domains} \
	localhost \
	127.0.0.1

# change permissions since we will mount the key into keycloak and the user will not match
chmod go+r "${key_file}"

# this adds the cert to a local PKCS12 keystore
# if mkcert had trouble to add it to gloabl java cacert store
# check if the JAVA_HOME environment variable is set correctly and try running the script again
# or add it manually (using the keytool in JAVA_HOME)
# (path_to_your_java) keytool -importcert -alias "${cert_alias}" -cacerts -storepass changeit -file "${cert_file}" -storepass changeit
echo "Importing into local java truststore: ${pkcs12_file}"
if [ -f "${pkcs12_file}" ]; then
	echo "Removing old trusted cert: ${cert_alias}"
	keytool -delete \
		-noprompt \
		-v \
		-alias "${cert_alias}" \
		-keystore "${pkcs12_file}" \
		-storetype PKCS12 \
		-storepass changeit \
		1>/dev/null 2>&1 \
		|| true
fi

echo "Adding new trusted cert: ${cert_alias}"
keytool -importcert \
    -noprompt \
    -v \
    -trustcacerts \
    -alias "${cert_alias}" \
    -keystore "${pkcs12_file}" \
    -storetype PKCS12 \
    -storepass changeit \
    -file "${cert_file}"

# Create individual certificate files for each domain
# (even though they are the same file right now we later might use individual certs, this will make migration easier)
for domain in ${domains}; do
    domain_cert_file="${certs_dir}/${domain}.pem"
    domain_key_file="${certs_dir}/${domain}.key"
    echo "Creating individual files for ${domain}"
    cp "${cert_file}" "${domain_cert_file}"
    cp "${key_file}" "${domain_key_file}"
done
