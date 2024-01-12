import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {
  private val mongoVer = "1.3.0"
  private val bootstrapVer = "7.22.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"        %% "bootstrap-backend-play-28" % bootstrapVer,
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-28"        % mongoVer,
    "uk.gov.hmrc"        %% "play-hal"                  % "3.4.0-play-28",
    "uk.gov.hmrc"        %% "agent-mtd-identifiers"     % "1.15.0",
    "uk.gov.hmrc"        %% "agent-kenshoo-monitoring"  % "5.5.0",
    "com.github.blemale" %% "scaffeine"                 % "5.2.1",
    "com.google.guava"   %  "guava"                     % "31.1-jre",
    "org.typelevel"      %% "cats-core"                 % "2.6.1"
  )

  val test = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play"       % "5.1.0"    % "test, it",
    "org.scalatestplus"      %% "mockito-3-12"             % "3.2.10.0" % "test, it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"  % mongoVer   % "test, it",
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"   % bootstrapVer % "test, it",
    "com.github.tomakehurst" %  "wiremock-jre8"            % "2.26.1"   % "test, it",
    "org.pegdown"            %  "pegdown"                  % "1.6.0"    % "test, it",
    "org.scalamock"          %% "scalamock"                % "5.1.0"    % "test, it",
    "com.typesafe.akka"      %% "akka-actor-testkit-typed" % "2.6.21"   % "test, it",
    "com.vladsch.flexmark"   %  "flexmark-all"             % "0.35.10"  % "test, it"
  )
}
