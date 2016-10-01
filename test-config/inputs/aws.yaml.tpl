image_id: "ami-47a23a30"
instance_type: "t2.small"
key_name: "Fill with yours"
security_groups: ["MyGroup1", "MyGroup2"]
plugin_configs:
  script:
    configuration:
      login: "ubuntu"
      key_content: |
        Fill with yours