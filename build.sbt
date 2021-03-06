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
    ScoverageKeys.coverageMinimum := 80.00,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val compileDeps = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-backend-play-27" % "5.6.0",
  "uk.gov.hmrc" %% "auth-client" % "5.6.0-play-27",
  "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.25.0-play-27",
  "com.github.blemale" %% "scaffeine" % "4.0.1",
  "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "4.6.0-play-27",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-27",
  "uk.gov.hmrc" %% "play-hal" % "2.1.0-play-27",
  "com.typesafe.play" %% "play-json-joda" % "2.7.4",
  "org.typelevel" %% "cats-core" % "2.3.0"
)

def testDeps(scope: String) = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.10.0-play-26" % scope,
  "org.scalatest" %% "scalatest" % "3.0.8" % scope,
  "org.mockito" % "mockito-core" % "3.2.0" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.22.0-play-27" % scope,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.27.1" % scope,
  "org.pegdown" % "pegdown" % "1.6.0" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "org.scalamock" %% "scalamock" % "4.4.0" % scope
)

def tmpMacWorkaround(): Seq[ModuleID] =
  if (sys.props.get("os.name").fold(false)(_.toLowerCase.contains("mac")))
    Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.16.1-osx-x86-64" % "runtime,test,it")
  else Seq()

lazy val root = (project in file("."))
  .settings(
    name := "agent-client-authorisation",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.10",
    PlayKeys.playDefaultPort := 9432,
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
    ),
    resolvers += "HMRC-local-artefacts-maven" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases-local",
    libraryDependencies ++= tmpMacWorkaround() ++ compileDeps ++ testDeps("test") ++ testDeps("it"),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.4.4" cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % "1.4.4" % Provided cross CrossVersion.full
    ),
    publishingSettings,
    scoverageSettings,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    routesImport ++= Seq("uk.gov.hmrc.agentclientauthorisation.binders.Binders._", "org.joda.time.LocalDate"),
    unmanagedSourceDirectories in Test += baseDirectory(_ / "testcommon").value,
    scalafmtOnCompile in Compile := true,
    scalafmtOnCompile in Test := true,
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
    Keys.fork in IntegrationTest := false,
    Defaults.itSettings,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "it").value,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "testcommon").value,
    parallelExecution in IntegrationTest := false,
    scalafmtOnCompile in IntegrationTest := true
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)

inConfig(IntegrationTest)(scalafmtCoreSettings)
