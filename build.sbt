name := "discord-vc-kick-bot"

version := "0.1"

resolvers ++= Seq(
  "Spring Plugins Repository" at "http://repo.spring.io/plugins-release/"
)

scalaVersion := "2.12.6"

libraryDependencies += "net.katsstuff" %% "ackcord" % "0.10.0"

