name := "pmd-isabelle"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "net.sourceforge.pmd" % "pmd-core" % "5.8.1",
  "info.hupel" %% "libisabelle-setup" % "0.9.2",
  "info.hupel" %% "pide-package" % "0.9.2",
  "org.slf4j" % "slf4j-jdk14" % "1.7.25"
)

fork in run := true
