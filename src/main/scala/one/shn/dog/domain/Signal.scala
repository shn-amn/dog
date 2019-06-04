package one.shn.dog.domain

import java.time.Instant

import one.shn.dog.config

import scala.io.AnsiColor

sealed trait Signal {
  def color:   String
  def message: String
}

case class Alert(
    timestamp: Instant,
    hitCount:  Int,
    threshold: Int)
  extends Signal {
  val color: String = AnsiColor.RED
  override def message: String =
    s"${config.fmt format timestamp} High traffic alert! $hitCount hits in past 2 minutes."
}

case class Recovery(
    timestamp: Instant,
    hitCount:  Int,
    threshold: Int)
  extends Signal {
  val color: String = AnsiColor.GREEN
  override def message: String =
    s"${config.fmt format timestamp} Back to normal traffic. $hitCount hits in past 2 minutes."
}
