package net.sanfra.framework

import com.fasterxml.jackson.databind.JsonNode
import scala.jdk.CollectionConverters._

case class ScenarioCfg(
  name:     String,
  weight:   Int,
  paths:    List[String],
  pauseMin: Int,
  pauseMax: Int
)

object SimulationConfig {

  def scenarios(cfg: JsonNode, fallbackPaths: List[String]): List[ScenarioCfg] = {
    val node = cfg.at("/scenarios")
    if (node.isMissingNode || !node.isArray)
      List(ScenarioCfg("generic_browse", 100, fallbackPaths, 500, 1500))
    else
      node.elements().asScala
        .filter(n => n.path("enabled").asBoolean(true))
        .map { n =>
          val paths =
            if (n.has("paths")) n.get("paths").elements().asScala.map(_.asText).toList
            else fallbackPaths
          ScenarioCfg(
            name     = n.get("name").asText("generic_browse"),
            weight   = n.path("weight").asInt(100),
            paths    = paths,
            pauseMin = n.path("pause").path("min").asInt(500),
            pauseMax = n.path("pause").path("max").asInt(1500)
          )
        }.toList
  }
}
