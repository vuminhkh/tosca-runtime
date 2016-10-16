image: "Put here your image id"
flavor: "2"
key_pair_name: "your keypair name"
security_group_names: ["your security group"]
external_network_name: "net-pub"
cloud_init: |
  #!/bin/sh
  sudo cp /etc/hosts /tmp/hosts
  echo 127.0.0.1 `hostname` | sudo tee /etc/hosts > /dev/null
  cat  /tmp/hosts | sudo tee -a /etc/hosts > /dev/null
