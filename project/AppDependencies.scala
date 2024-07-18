import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {
  private val mongoVer = "1.9.0"
  private val bootstrapVer = "8.6.0"
  private val pekkoVersion = "1.0.2"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"        %% "bootstrap-backend-play-30" % bootstrapVer,
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-30"        % mongoVer,
    "uk.gov.hmrc"        %% "play-hal-play-30"          % "4.0.0",
    "uk.gov.hmrc"        %% "agent-mtd-identifiers"     % "2.0.0",
    "com.github.blemale" %% "scaffeine"                 % "5.2.1",
    "com.google.guava"   %  "guava"                     % "33.2.1-jre",
    "org.typelevel"      %% "cats-core"                 % "2.12.0"
  )

  val test = Seq(
    "org.scalatestplus.play"  %% "scalatestplus-play"         % "7.0.1"       % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % mongoVer      % Test,
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVer  % Test,
    "org.pegdown"             %  "pegdown"                    % "1.6.0"       % Test,
    "org.scalamock"           %% "scalamock"                  % "6.0.0"       % Test,
    "org.apache.pekko"        %% "pekko-stream-testkit"       % pekkoVersion  % Test,
    "org.apache.pekko"        %% "pekko-actor-testkit-typed"  % pekkoVersion  % Test,
    "com.vladsch.flexmark"    % "flexmark-all"                % "0.64.8"      % Test
  )
}
