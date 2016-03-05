How to launch the web app in debug mode with Intellij ?
- Create SBT tasks launcher with task ~deployer/run
- Add following properties to override existing configuration for H2 :

  ``` bash
  -Dcom.toscaruntime.workspace="./deployer/test/resources/testWorkspace" -Dslick.dbs.default.db.url="jdbc:h2:file:./deployer/test/resources/testWorkspace/data/db"
  ```
- You can now launch the created configuration in debug mode
- The created deployment is a mock load balancer web app topology that does nothing