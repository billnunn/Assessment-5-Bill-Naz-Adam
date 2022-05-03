ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.11.12"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.8"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.4.8"
//libraryDependencies += "graphframes" % "graphframes" % "0.8.1-spark2.4-s_2.11"
libraryDependencies += "org.apache.spark" %% "spark-graphx" % "2.4.8"

lazy val root = (project in file("."))
  .settings(
    name := "sparktest"
  )
