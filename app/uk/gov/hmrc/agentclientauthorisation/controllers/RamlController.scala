package uk.gov.hmrc.agentclientauthorisation.controllers

import uk.gov.hmrc.api.controllers.DocumentationController


class RamlController extends DocumentationController {

  def raml(version: String, file: String) = {
    super.at(s"/public/api/conf/$version", file)
  }
}


