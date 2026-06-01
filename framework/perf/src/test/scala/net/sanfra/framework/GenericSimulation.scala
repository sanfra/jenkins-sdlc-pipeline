package net.sanfra.framework

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

/**
 * GenericSimulation — fully config-driven, zero hardcoded values.
 *
 * Config resolution (later overrides earlier):
 *   metrics/base.yml
 *   metrics/profile-{app.techProfile}.yml
 *   metrics/env-{env}.yml
 *   metrics/criticality-{app.criticality}.yml
 *   -D system properties (highest priority)
 *
 * Scenarios are defined in the YAML per tech profile and documented
 * as Gherkin specifications in resources/features/generic-simulation.feature.
 * Each scenario name in YAML must match a @scenario tag in the feature file.
 */
class GenericSimulation extends Simulation {

  private val mapper = new ObjectMapper(new YAMLFactory()).registerModule(DefaultScalaModule)

  private def loadResource(path: String): JsonNode =
    Option(getClass.getClassLoader.getResourceAsStream(path))
      .map(mapper.readTree).getOrElse(mapper.createObjectNode())

  private def merge(base: JsonNode, over: JsonNode): JsonNode = {
    if (!base.isObject || !over.isObject) return over
    val result = base.deepCopy[ObjectNode]()
    over.fields().asScala.foreach { e =>
      val existing = result.get(e.getKey)
      val merged =
        if (existing != null && existing.isObject && e.getValue.isObject) merge(existing, e.getValue)
        else e.getValue
      result.set[ObjectNode](e.getKey, merged)
    }
    result
  }

  private val techProfile = sys.props.getOrElse("app.techProfile", "spa")
  private val env         = sys.props.getOrElse("env",             "ci")
  private val criticality = sys.props.getOrElse("app.criticality", "low")
  private val baseUrl     = sys.props.getOrElse("app.baseUrl",     "http://localhost:4173")
  private val appPaths    = sys.props.getOrElse("app.paths", "/").split(",").map(_.trim).toList

  private val cfg: JsonNode =
    List(s"metrics/base.yml",
         s"metrics/profile-$techProfile.yml",
         s"metrics/env-$env.yml",
         s"metrics/criticality-$criticality.yml")
    .map(loadResource).reduce(merge)

  private def int(path: String, default: Int): Int = {
    val n = cfg.at(path); if (n.isMissingNode || n.isNull) default else n.intValue()
  }

  private val warmupUsers  = int("/load/warmup/users",         2)
  private val warmupDur    = int("/load/warmup/duration",      10)
  private val rampTo       = int("/load/rampUp/toUsers",        5)
  private val rampDur      = int("/load/rampUp/duration",      30)
  private val plateauUsers = int("/load/plateau/users",         5)
  private val plateauDur   = int("/load/plateau/duration",     60)
  private val rampDownDur  = int("/load/rampDown/duration",    15)

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("text/html,application/xhtml+xml,application/json")
    .disableFollowRedirect

  private def injectFor(s: ScenarioCfg) = {
    val w          = s.weight / 100.0
    val warmRps    = math.max(0.1, warmupUsers  * w / 10.0)
    val rampToRps  = math.max(0.3, rampTo       * w / 10.0)
    val plateauRps = math.max(0.3, plateauUsers * w / 10.0)
    ScenarioChains.build(s).inject(
      constantUsersPerSec(warmRps).during(warmupDur.seconds),
      rampUsersPerSec(warmRps).to(rampToRps).during(rampDur.seconds),
      constantUsersPerSec(plateauRps).during(plateauDur.seconds),
      rampUsersPerSec(plateauRps).to(0).during(rampDownDur.seconds)
    )
  }

  setUp(SimulationConfig.scenarios(cfg, appPaths).map(injectFor): _*)
    .protocols(httpProtocol)
    .assertions(
      global.responseTime.mean.lt(int("/assertions/meanRtMaxMs",         3000)),
      global.responseTime.percentile(95).lt(int("/assertions/p95RtMaxMs", 8000)),
      global.responseTime.percentile(99).lt(int("/assertions/p99RtMaxMs",12000)),
      global.responseTime.max.lt(int("/assertions/maxRtMaxMs",           20000)),
      global.successfulRequests.percent.gt(int("/assertions/minSuccessPercent", 90).toDouble)
    )
}
