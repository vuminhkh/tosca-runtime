logLevel := Level.Warn

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "Typesafe Ivy repository" at "http://repo.typesafe.com/typesafe/ivy-releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.5")