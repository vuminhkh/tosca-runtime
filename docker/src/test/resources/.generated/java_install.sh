#!/bin/sh -e
export JAVA_HOME=/opt/java
export JAVA_URL=http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz
chmod +x /var/recipe/java_install.sh
/var/recipe/java_install.sh
