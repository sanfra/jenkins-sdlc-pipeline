package net.sanfra.framework

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Builds Gatling ScenarioBuilders from ScenarioCfg.
 * Named scenarios can carry custom checks; the default handles any path list.
 * To add app-specific behaviour, extend the match block with a new case.
 */
object ScenarioChains {

  def build(s: ScenarioCfg): ScenarioBuilder = s.name match {
    case "browse_home"   => singlePage(s, "/",        "home")
    case "contact_visit" => singlePage(s, "/contact", "contact")
    case _               => multiPage(s)
  }

  private def singlePage(s: ScenarioCfg, path: String, label: String) =
    scenario(s.name)
      .exec(http(label)
        .get(path)
        .check(status.in(200, 304)))
      .pause(s.pauseMin.milliseconds, s.pauseMax.milliseconds)

  private def multiPage(s: ScenarioCfg) =
    scenario(s.name)
      .foreach(s.paths, "path") {
        exec(http("GET #{path}")
          .get("#{path}")
          .check(status.in(200, 304)))
        .pause(s.pauseMin.milliseconds, s.pauseMax.milliseconds)
      }
}
