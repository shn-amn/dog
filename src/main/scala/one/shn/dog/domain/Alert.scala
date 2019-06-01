package one.shn.dog.domain

import java.time.Instant

sealed trait Alert {
  def message: String
}

case class Busy(
    timestamp: Instant,
    hitCount:  Int,
    threshold: Int)
  extends Alert {
  override def message: String = toString
}

case class Normal(
    timestamp: Instant,
    hitCount:  Int,
    threshold: Int)
  extends Alert {
  override def message: String = toString
}
