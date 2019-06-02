package one.shn.dog.domain

import java.time.Instant

import scala.language.postfixOps

case class Stats(timestamp: Instant, logs: Vector[Log]) {

  private lazy val hits = logs groupBy (_.section) mapValues (_.length)
  private lazy val maxHits = hits.values.max
  private lazy val popSections = hits collect { case (section, n) if n == maxHits => section }

  val count: Int = logs.length

  def message: String =
    if (logs.isEmpty) s"$timestamp: No hits."
    else popSections match {
      case head :: Nil =>
        s"$timestamp: $count hits. Most popular section $head with ${100 * maxHits / count}%."
      case head :: tail =>
        s"$timestamp: $count hits. Most popular sections ${tail mkString ", "} and $head with ${100 * maxHits / count}% each."
    }

}
