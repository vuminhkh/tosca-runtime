# Copy this file and rename it to bootstrap_openstack.yaml, then configure your test configuration
image: "Fill with yours"
flavor: "2"
key_pair_name: "Fill with yours"
security_groups: ["MyGroup1", "MyGroup2"]
external_network_name: "Fill with yours"
swarm_nodes_count: 1
cloud_init: |
  Fill with yours
plugin_configs:
  script:
    configuration:
      user: "ubuntu"
      key_content: |
        Fill with yours