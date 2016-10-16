# Copy this file and rename it to openstack.yaml, then configure your test configuration
image: "Fill with yours"
flavor: "2"
key_pair_name: "Fill with yours"
security_group_names: ["MyGroup1", "MyGroup2"]
external_network_name: "Fill with yours"
plugin_configs:
  script:
    configuration:
      user: "Fill with yours"
      key_content: |
        Fill with yours