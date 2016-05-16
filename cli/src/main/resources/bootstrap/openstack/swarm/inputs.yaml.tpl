image: "Swarm needs recent linux kernel, for example for Ubuntu it only works for Ubuntu Vivid and above"
flavor: "2"
key_content: |
  -----BEGIN RSA PRIVATE KEY-----
  Put here your private key
  -----END RSA PRIVATE KEY-----

login: "ubuntu"
key_pair_name: "your keypair name"
security_group_names: ["openbar"]
external_network_name: "net-pub"
cloud_init: |
  #!/bin/sh
  sudo cp /etc/hosts /tmp/hosts
  echo 127.0.0.1 `hostname` | sudo tee /etc/hosts > /dev/null
  cat  /tmp/hosts | sudo tee -a /etc/hosts > /dev/null
swarm_nodes_count: 2