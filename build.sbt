import sbt.{IntegrationTest, inConfig}
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimumStmtTotal := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}

lazy val root = (project in file("."))
  .settings(
    name := "agent-client-authorisation",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.13.10",
    majorVersion := 1,
    PlayKeys.playDefaultPort := 9432,
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
    ),
    libraryDependencies ++=  AppDependencies.compile ++ AppDependencies.test,
    routesImport ++= Seq("uk.gov.hmrc.agentclientauthorisation.binders.Binders._", "java.time.LocalDate"),
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    scalacOptions ++= Seq(
      "-Xlint:-missing-interpolator,_",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-Wconf:src=target/.*:s", // silence warnings from compiled files
      "-Wconf:src=Routes/.*:s"  // silence warnings from routes files
      )
  )
  .settings(
    scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / unmanagedSourceDirectories += baseDirectory(_ / "testcommon").value
  )
  .configs(IntegrationTest)
  .settings(
    IntegrationTest / Keys.fork := true,
    Defaults.itSettings,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory(_ / "it").value,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory(_ / "testcommon").value,
    IntegrationTest / parallelExecution := false,
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)

inConfig(IntegrationTest)(scalafmtCoreSettings)
