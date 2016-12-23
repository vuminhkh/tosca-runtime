// Any configuration here can be overidden per node by setting properties / input in the topology
// Configure connection to the ansible control machine as a docker connection
// control_machine: {
//   connection_type: "docker"
//   target: "ansible"
//   DOCKER_HOST: "tcp://192.168.99.100:2376"
//   DOCKER_TLS_VERIFY="1"
//   # A directory "cert" should exist at the same level as this configuration file and should contain all the 3 keys ca.pem, cert.pem and key.pem
//   DOCKER_CERT_PATH = ${com.toscaruntime.target.dir}"/cert"
// }
// Configure connection to the target VM
control_machine: {
   connection_type: "local"
}
port: "22"
// data_dir: "/home/toscaruntime"