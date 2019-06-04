package one.shn.dog.domain

import java.time.Instant
import java.time.format.DateTimeFormatter

import scala.language.postfixOps

case class Stats(timestamp: Instant, logs: Vector[Log]) {

  private lazy val hits = logs groupBy (_.section) mapValues (_.length)
  private lazy val maxHits = hits.values.max
  private lazy val popSections = hits collect { case (section, n) if n == maxHits => section }

  val count: Int = logs.length

  def message(implicit fmt: DateTimeFormatter): String =
    if (logs.isEmpty) s"${fmt format timestamp} No hits."
    else popSections match {
      case head :: Nil => // singular
        s"${fmt format timestamp} $count hits. Most popular section $head with ${100 * maxHits / count}%."
      case head :: tail => // plural with "and" in the end!
        s"${fmt format timestamp} $count hits. Most popular sections ${tail mkString ", "} and $head with ${100 * maxHits / count}% each."
    }

}
