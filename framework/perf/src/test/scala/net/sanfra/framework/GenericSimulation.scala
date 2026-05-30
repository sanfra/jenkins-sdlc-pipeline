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
 * Required system properties:
 *   -Dapp.baseUrl        base URL of the app under test
 *   -Dapp.techProfile    spa | rest-api | ...
 *   -Dapp.criticality    low | medium | high
 *   -Denv                ci | dev | qa | staging | preprod
 *
 * Optional:
 *   -Dapp.paths          comma-separated paths to load (default: /)
 */
class GenericSimulation extends Simulation {

  // ── YAML loader & deep-merge ────────────────────────────────────────────────

  private val mapper = new ObjectMapper(new YAMLFactory())
    .registerModule(DefaultScalaModule)

  private def loadResource(path: String): JsonNode =
    Option(getClass.getClassLoader.getResourceAsStream(path))
      .map(mapper.readTree)
      .getOrElse(mapper.createObjectNode())

  private def merge(base: JsonNode, over: JsonNode): JsonNode = {
    if (!base.isObject || !over.isObject) return over
    val result = base.deepCopy[ObjectNode]()
    over.fields().asScala.foreach { e =>
      val existing = result.get(e.getKey)
      val merged =
        if (existing != null && existing.isObject && e.getValue.isObject)
          merge(existing, e.getValue)
        else e.getValue
      result.set[ObjectNode](e.getKey, merged)
    }
    result
  }

  // ── Parameters ──────────────────────────────────────────────────────────────

  private val techProfile = sys.props.getOrElse("app.techProfile", "spa")
  private val env         = sys.props.getOrElse("env", "ci")
  private val criticality = sys.props.getOrElse("app.criticality", "low")
  private val baseUrl     = sys.props.getOrElse("app.baseUrl", "http://localhost:4173")
  private val paths       = sys.props.getOrElse("app.paths", "/").split(",").map(_.trim).toList

  // ── Merged config ───────────────────────────────────────────────────────────

  private val cfg: JsonNode = {
    val base  = loadResource("metrics/base.yml")
    val prof  = loadResource(s"metrics/profile-$techProfile.yml")
    val envN  = loadResource(s"metrics/env-$env.yml")
    val crit  = loadResource(s"metrics/criticality-$criticality.yml")
    merge(merge(merge(base, prof), envN), crit)
  }

  private def int(jsonPath: String, default: Int): Int = {
    val node = cfg.at(jsonPath)
    if (node.isMissingNode || node.isNull) default else node.intValue()
  }

  // ── Load profile ─────────────────────────────────────────────────────────────

  private val warmupUsers   = int("/load/warmup/users", 2)
  private val warmupDur     = int("/load/warmup/duration", 10)
  private val rampFrom      = int("/load/rampUp/fromUsers", 2)
  private val rampTo        = int("/load/rampUp/toUsers", 5)
  private val rampDur       = int("/load/rampUp/duration", 30)
  private val plateauUsers  = int("/load/plateau/users", 5)
  private val plateauDur    = int("/load/plateau/duration", 60)
  private val rampDownFrom  = int("/load/rampDown/fromUsers", 5)
  private val rampDownDur   = int("/load/rampDown/duration", 15)

  // ── Assertions ────────────────────────────────────────────────────────────────

  private val meanRt     = int("/assertions/meanRtMaxMs", 3000)
  private val p95Rt      = int("/assertions/p95RtMaxMs", 8000)
  private val p99Rt      = int("/assertions/p99RtMaxMs", 12000)
  private val maxRt      = int("/assertions/maxRtMaxMs", 20000)
  private val successPct = int("/assertions/minSuccessPercent", 90)

  // ── Scenario ──────────────────────────────────────────────────────────────────

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("text/html,application/xhtml+xml,application/json")
    .disableFollowRedirect

  private val loadScenario = scenario("generic-load")
    .foreach(paths, "path") {
      exec(http("GET #{path}")
        .get("#{path}")
        .check(status.in(200, 304)))
      .pause(500.milliseconds, 1500.milliseconds)
    }

  // ── Setup ─────────────────────────────────────────────────────────────────────

  setUp(
    loadScenario.inject(
      constantUsersPerSec(math.max(0.5, warmupUsers / 10.0)).during(warmupDur.seconds),
      rampUsersPerSec(math.max(0.1, rampFrom / 10.0)).to(math.max(0.5, rampTo / 10.0)).during(rampDur.seconds),
      constantUsersPerSec(math.max(0.5, plateauUsers / 10.0)).during(plateauDur.seconds),
      rampUsersPerSec(math.max(0.5, rampDownFrom / 10.0)).to(0).during(rampDownDur.seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.mean.lt(meanRt),
      global.responseTime.percentile(95).lt(p95Rt),
      global.responseTime.percentile(99).lt(p99Rt),
      global.responseTime.max.lt(maxRt),
      global.successfulRequests.percent.gt(successPct.toDouble)
    )
}
