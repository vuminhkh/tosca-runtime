# Copy this file and rename it to bootstrap_aws.yaml, then configure your test configuration
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
      user: "ubuntu"
      key_content: |
        Fill with yours