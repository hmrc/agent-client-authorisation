package uk.gov.hmrc.agentclientauthorisation.support

import org.scalatest.Suite
import reactivemongo.api.FailoverStrategy
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

trait MongoApp extends MongoSpecSupport {
  me: Suite =>

  protected def mongoConfiguration = Map("mongodb.uri" -> mongoUri)

  override implicit lazy val mongoConnectorForTest: MongoConnector =
    MongoConnector(mongoUri, Some(MongoApp.failoverStrategyForTest))
}

object MongoApp {

  import scala.concurrent.duration._
  val failoverStrategyForTest = FailoverStrategy(1000.millis, 5, _ * 1.618)

}
