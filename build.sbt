import play.core.PlayVersion
import sbt.Tests.{Group, SubProcess}
import sbt.{IntegrationTest, inConfig}
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
  "uk.gov.hmrc" %% "bootstrap-play-25" % "4.11.0",
  "uk.gov.hmrc" %% "auth-client" % "2.21.0-play-25",
  "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.17.0-play-25",
  "com.github.blemale" %% "scaffeine" % "2.6.0",
  "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "3.8.0",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.20.0-play-25",
  "uk.gov.hmrc" %% "play-config" % "7.5.0",
  "uk.gov.hmrc" %% "play-hal" % "1.8.0-play-25"
)

def testDeps(scope: String) = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.8.0-play-25" % scope,
  "org.scalatest" %% "scalatest" % "3.0.7" % scope,
  "org.mockito" % "mockito-core" % "2.27.0" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.14.0-play-25" % scope,
  "com.github.tomakehurst" % "wiremock" % "2.23.2" % scope,
  "org.pegdown" % "pegdown" % "1.6.0" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "org.scalamock" %% "scalamock" % "4.1.0" % scope
)

def tmpMacWorkaround(): Seq[ModuleID] =
  if (sys.props.get("os.name").fold(false)(_.toLowerCase.contains("mac")))
    Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.16.1-osx-x86-64" % "runtime,test,it")
  else Seq()

lazy val root = (project in file("."))
  .settings(
    name := "agent-client-authorisation",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.11.11",
    PlayKeys.playDefaultPort := 9432,
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.bintrayRepo("hmrc", "release-candidates"),
      Resolver.typesafeRepo("releases"),
      Resolver.jcenterRepo
    ),
    libraryDependencies ++= tmpMacWorkaround() ++ compileDeps ++ testDeps("test") ++ testDeps("it"),
    publishingSettings,
    scoverageSettings,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    routesImport ++= Seq("uk.gov.hmrc.agentclientauthorisation.binders.Binders._", "org.joda.time.LocalDate"),
    unmanagedSourceDirectories in Test += baseDirectory(_ / "testcommon").value,
    scalafmtOnCompile in Compile := true,
    scalafmtOnCompile in Test := true,
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,_",
      "-Yno-adapted-args",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions")
  )
  .configs(IntegrationTest)
  .settings(
    majorVersion:=0,
    Keys.fork in IntegrationTest := false,
    Defaults.itSettings,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "it").value,
    unmanagedSourceDirectories in IntegrationTest += baseDirectory(_ / "testcommon").value,
    parallelExecution in IntegrationTest := false,
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    scalafmtOnCompile in IntegrationTest := true
  )
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)

inConfig(IntegrationTest)(scalafmtCoreSettings)

def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] = {
  tests.map { test =>
    Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq(s"-Dtest.name=${test.name}"))))
  }
}