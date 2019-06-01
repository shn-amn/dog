package one.shn.dog.domain

import java.time.Instant

case class Stats(timestamp: Instant, logs: Vector[Log]) {

  private val hits = logs groupBy (_.section) mapValues (_.length)
  private val maxHits = hits.values.max

  val count: Int = logs.length

  def message: String =
    hits collect { case (section, n) if n == maxHits => section } mkString ", "

}
