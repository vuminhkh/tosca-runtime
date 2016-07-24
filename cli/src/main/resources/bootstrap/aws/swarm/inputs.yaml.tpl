image_id: "ami-71e88902"
instance_type: "t2.small"
key_content: |
  -----BEGIN RSA PRIVATE KEY-----
  Put here your private key
  -----END RSA PRIVATE KEY-----

login: "ubuntu"
key_name: "your key name"
security_groups: ["your security groups"]
cloud_init: |
  #!/bin/sh
  sudo cp /etc/hosts /tmp/hosts
  echo 127.0.0.1 `hostname` | sudo tee /etc/hosts > /dev/null
  cat  /tmp/hosts | sudo tee -a /etc/hosts > /dev/null