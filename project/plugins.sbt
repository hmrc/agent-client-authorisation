resolvers += Resolver.url("hmrc-sbt-plugin-releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.0.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "467a8b417f9d90ab863f3172f1bebe1d7c49706b")

addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "0.11.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.10")
