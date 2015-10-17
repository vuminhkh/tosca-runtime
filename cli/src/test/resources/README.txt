Basic commands:

Compile wordpress:

compile -o /Users/vuminhkh/wordpress-dep/deployment/recipe -t /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/topologies/wordpress -cp /Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/apache:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/mysql:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/php:/Users/vuminhkh/Projects/tosca-runtime/test/src/test/resources/components/wordpress -p /Users/vuminhkh/Projects/tosca-runtime/docker/target/universal/stage/

Package a compiled recipe to a docker image:

package -d /Users/vuminhkh/wordpress-dep -u https://192.168.99.100:2376 -c /Users/vuminhkh/.docker/machine/machines/default

List deployment docker images:

deployments -u https://192.168.99.100:2376 -c /Users/vuminhkh/.docker/machine/machines/default

Create deployment agent:

deploy -i wordpress -u https://192.168.99.100:2376 -c /Users/vuminhkh/.docker/machine/machines/default