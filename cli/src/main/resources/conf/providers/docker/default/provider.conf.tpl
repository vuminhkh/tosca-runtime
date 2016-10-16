DOCKER_HOST = "fill with configured docker daemon URL"
DOCKER_TLS_VERIFY = "1"
# A directory "cert" should exist at the same level as this configuration file and should contain all the 3 keys ca.pem, cert.pem and key.pem
DOCKER_CERT_PATH = ${com.toscaruntime.target.dir}"/cert"