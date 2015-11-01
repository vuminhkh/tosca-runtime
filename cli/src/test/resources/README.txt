Basic commands:

Compile wordpress:

compile -p docker -o /Users/vuminhkh/Data/worpress.zip -cp /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/apache:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/mysql:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/php:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/wordpress -t /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/topologies/wordpress

compile -p openstack -o /Users/vuminhkh/Data/worpress-os.zip -cp /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/apache:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/mysql:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/php:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/wordpress -t /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/topologies/wordpress-os

Package a compiled recipe to a docker image:

package -n wordpress-dev -r /Users/vuminhkh/Data/worpress.zip -p docker

package -n wordpress-os -r /Users/vuminhkh/Data/worpress-os.zip -p openstack

List deployment docker images:

deployments

Create deployment agent:

deploy -i wordpress-dev

deploy -i wordpress-os