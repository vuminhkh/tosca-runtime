# Copy this file and rename it to aws.yaml, then configure your test configuration
image_id: "ami-47a23a30"
instance_type: "t2.small"
key_name: "Fill with yours"
security_groups: ["MyGroup1", "MyGroup2"]
plugin_configs:
  script:
    configuration:
      user: "ubuntu"
      key_content: |
        Fill with yours