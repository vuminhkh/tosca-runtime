image_id: "ami-71e88902"
instance_type: "t2.small"
key_name: "Fill with yours"
security_groups: ["MyGroup1", "MyGroup2"]
swarm_nodes_count: 1
cloud_init: |
  Fill with yours
plugin_configs:
  script:
    configuration:
      login: "ubuntu"
      key_content: |
        Fill with yours