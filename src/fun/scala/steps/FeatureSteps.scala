/**
 * Copyright 2014 Marco Vermeulen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package steps

import cucumber.api.scala.{EN, ScalaDsl}
import org.scalatest.ShouldMatchers
import steps.World._
import support.{Http, Mongo}

import scalaj.http.HttpException

class FeatureSteps extends ScalaDsl with EN with ShouldMatchers {

  Before() { s =>
    candidateColl = Mongo.createCollection(mongoDb, "candidates")
    versionColl = Mongo.createCollection(mongoDb, "versions")
  }

  After() { s =>
    Mongo.dropCollection(candidateColl)
    Mongo.dropCollection(versionColl)
  }

  Given( """^endpoint "(.*?)" exchanges credentials "(.*?)" and "(.*?)" for a bearer token$""") { (endpoint: String, username: String, password: String) =>
    val str = Http.obtainToken(endpoint, username, password).asString
    val bt = mapper.readValue[Map[String, String]](str).getOrElse("access_token", "invalid_token")
    token = s"Bearer $bt"
  }

  When( """^a JSON POST on the "(.*)" endpoint:$""") { (endpoint: String, json: String) =>
    request = Http.postJson(endpoint, json.stripMargin, token)
  }

  Then( """^the status received is "(.*)"$""") { (status: String) =>
    request.responseCode shouldBe statusCodes(status)
  }

  Then( """^a valid identifier is received in the response$""") { () =>
    val Pattern = "([0-9a-fA-F]{24})".r
    mapper.readValue[Map[String, String]](request.asString).get("id") match {
      case Some(Pattern(id)) => println(s"Valid identifier found: $id")
      case Some(id) => fail(s"The id $id is not a valid ObjectID")
      case None => fail("No id found.")
    }
  }

  Then( """^"(.*?)" Version "(.*?)" with URL "(.*?)" was published$""") { (candidate: String, version: String, url: String) =>
    Mongo.versionPublished(versionColl, candidate, version, url) shouldBe true
  }

  Given( """^a "(.*?)" Version "(.*?)" with URL "(.*?)" already exists$""") { (candidate: String, version: String, url: String) =>
    Mongo.saveVersion(versionColl, candidate, version, url)
  }

  Given( """^the existing Default "(.*?)" Version is "(.*?)"$""") { (candidate: String, version: String) =>
    Mongo.saveCandidate(candidateColl, candidate, version)
  }

  When( """^a JSON PUT on the "(.*?)" endpoint:$""") { (endpoint: String, payload: String) =>
    request = Http.putJson(endpoint, payload.stripMargin, token)
  }

  Then( """^the message "(.*?)" is received$""") { (message: String) =>
    try {
      extractMessage(request.asString) shouldBe message
    } catch {
      case e: HttpException => extractMessage(e.body) shouldBe message
    }
  }

  Then( """^the error message received includes "(.*?)"$""") { (message: String) =>
    try {
      extractMessage(request.asString) should include(message)
    } catch {
      case e: HttpException => extractMessage(e.body) should include(message)
    }
  }

  def extractMessage(str: String) = mapper.readValue[Map[String, String]](str).getOrElse("message", "invalid")

  Then( """^the Default "(.*?)" Version has changed to "(.*?)"$""") { (candidate: String, version: String) =>
    Mongo.isDefault(candidateColl, candidate, version) shouldBe true
  }

  Given( """^Candidate "(.*?)" Version "(.*?)" does not exists$""") { (candidate: String, version: String) =>
    Mongo.versionExists(versionColl, candidate, version) shouldBe false
  }

  Given( """^Candidate "(.*?)" does not exist$""") { (candidate: String) =>
    Mongo.candidateExists(candidateColl, candidate) shouldBe false
  }

  Given( """^the appropriate candidate already exists$""") { () =>
    Mongo.saveCandidate(candidateColl, "groovy", "2.3.6")
  }
}