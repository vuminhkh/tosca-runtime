Basic commands:

Compile wordpress:

compile -p docker -o /Users/vuminhkh/Data/worpress.zip -cp /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/apache:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/mysql:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/php:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/wordpress -t /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/topologies/wordpress

compile -p openstack -o /Users/vuminhkh/Data/worpress-os.zip -cp /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/apache:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/mysql:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/php:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/wordpress -t /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/topologies/wordpress-os

Package a compiled recipe to a docker image:

package -n wordpress-dk -r /Users/vuminhkh/Data/worpress.zip -p docker

package -n wordpress-os -r /Users/vuminhkh/Data/worpress-os.zip -p openstack -i /Users/vuminhkh/Projects/tosca-runtime/cli/target/universal/stage/bootstrap/openstack/default/inputs.yml

List deployment docker images:

deployments

Create deployment agent:

deployments run wordpress-dk

deployments run wordpress-os