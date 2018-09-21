name := "discord-vc-kick-bot"

version := "0.1"
scalaVersion := "2.12.6"

resolvers ++= Seq("Spring Plugins Repository" at "http://repo.spring.io/plugins-release/")
libraryDependencies ++= Seq("net.katsstuff" %% "ackcord" % "0.11.0-SNAPSHOT")

enablePlugins(DockerPlugin)

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

imageNames in docker := Seq(
  ImageName(s"kory33/${name.value}:latest"),
  ImageName(s"kory33/${name.value}:v${version.value}")
)
