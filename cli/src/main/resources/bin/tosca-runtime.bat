@echo off
SETLOCAL
SET SCRIPT_DIR=%~dp0
cd %SCRIPT_DIR%
cd ..
SET BASE_DIR=%CD%
cd %SCRIPT_DIR%
echo "Setting tosca runtime base dir to %BASE_DIR%"
SET TOSCA_RUNTIME_OPTS=-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -Dtoscaruntime.clientMode=true -Dtoscaruntime.basedir="%BASE_DIR%" %TOSCA_RUNTIME_OPTS%
SET CONFIG_FILE=%BASE_DIR%\conf\launchConfig
SET JAVA_CMD=java %TOSCA_RUNTIME_OPTS% -jar "%SCRIPT_DIR%\sbt-launch.jar" "@file:///%CONFIG_FILE:\=/%" %*
echo "Command is %JAVA_CMD%"
%JAVA_CMD%