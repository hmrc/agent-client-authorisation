import play.core.PlayVersion
import sbt.{CrossVersion, IntegrationTest, inConfig}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
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

val silencerVersion = "1.7.8"

lazy val compileDeps = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-backend-play-28" % "7.12.0",
  "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.49.0-play-28",
  "com.github.blemale" %% "scaffeine" % "4.0.1",
  "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "4.8.0-play-28",
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28" % "0.74.0",
  "uk.gov.hmrc" %% "play-hal" % "3.2.0-play-28",
  "com.google.guava" % "guava" % "31.1-jre",
  "org.typelevel" %% "cats-core" % "2.6.1"
)

def testDeps(scope: String) = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % scope,
  "org.scalatestplus" %% "mockito-3-12" % "3.2.10.0" % scope,
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.74.0" % scope,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.26.1" % scope,
  "org.pegdown" % "pegdown" % "1.6.0" % scope,
  "org.scalamock" %% "scalamock" % "5.1.0" % scope,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.20" % scope,
  "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % scope
)


lazy val root = (project in file("."))
  .settings(
    name := "agent-client-authorisation",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.15",
    PlayKeys.playDefaultPort := 9432,
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
    ),
    resolvers += "HMRC-local-artefacts-maven" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases-local",
    libraryDependencies ++=  compileDeps ++ testDeps("test") ++ testDeps("it"),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    ),
    publishingSettings,
    scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    routesImport ++= Seq("uk.gov.hmrc.agentclientauthorisation.binders.Binders._", "java.time.LocalDate"),
    Test / unmanagedSourceDirectories += baseDirectory(_ / "testcommon").value,
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Ypartial-unification",
      "-Xlint:-missing-interpolator,_",
      "-Yno-adapted-args",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-P:silencer:pathFilters=Routes.scala")
  )
  .configs(IntegrationTest)
  .settings(
    majorVersion:=0,
    IntegrationTest / Keys.fork := true,
    Defaults.itSettings,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory(_ / "it").value,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory(_ / "testcommon").value,
    IntegrationTest / parallelExecution := false,
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)

inConfig(IntegrationTest)(scalafmtCoreSettings)
