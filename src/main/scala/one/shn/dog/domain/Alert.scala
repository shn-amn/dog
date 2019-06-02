package one.shn.dog.domain

import java.time.Instant

import scala.io.AnsiColor

sealed trait Alert {
  def color:   String
  def message: String
}

case class Busy(
    timestamp: Instant,
    hitCount:  Int,
    threshold: Int)
  extends Alert {
  val color: String = AnsiColor.RED
  override def message: String =
    s"$timestamp: High traffic generated an alert. $hitCount hits in 2 minutes triggered an alert at $timestamp."
}

case class Normal(
    timestamp: Instant,
    hitCount:  Int,
    threshold: Int)
  extends Alert {
  val color: String = AnsiColor.GREEN
  override def message: String =
    s"$timestamp: Traffic is normal now. $hitCount hits in past two minutes."
}
