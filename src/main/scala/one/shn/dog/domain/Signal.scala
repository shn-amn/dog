package one.shn.dog.domain

import java.time.Instant
import java.time.format.DateTimeFormatter

import scala.io.AnsiColor

sealed trait Signal {
  def color: String
  def message(implicit fmt :DateTimeFormatter): String
}

case class Alert(
    timestamp: Instant,
    hitCount:  Int,
    threshold: Int)
  extends Signal {
  val color: String = AnsiColor.RED
  override def message(implicit fmt: DateTimeFormatter): String =
    s"${fmt format timestamp} High traffic alert! $hitCount hits in past 2 minutes."
}

case class Recovery(
    timestamp: Instant,
    hitCount:  Int,
    threshold: Int)
  extends Signal {
  val color: String = AnsiColor.GREEN
  override def message(implicit fmt: DateTimeFormatter): String =
    s"${fmt format timestamp} Back to normal traffic. $hitCount hits in past 2 minutes."
}
