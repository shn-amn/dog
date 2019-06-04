package one.shn.dog.domain

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

import org.scalatest.FlatSpec

// These tests are mainly for testing the right formatting alert messages.
class StatsSpec extends FlatSpec {

  implicit val fmt = DateTimeFormatter ofPattern "HH:mm:ss" withZone (ZoneId of "Europe/Paris")
  
  private val timestamp = Instant ofEpochMilli 0
  private val formatted = fmt format timestamp
  
  s"Stats($timestamp, Vector.empty)" should s"""read "$formatted No hits."""" in
    assert(Stats(timestamp, Vector.empty).message == s"$formatted No hits.")

  s"Stats($timestamp, Vector(log))" should "have one popular section." in
    assert(Stats(timestamp, Vector(Log(timestamp, "/"))).message endsWith "100%.")

  s"Stats($timestamp, Vector(log1, log2)" should "have two popular sections." in
    assert(Stats(timestamp, Vector(Log(timestamp, "/"), Log(timestamp, "/hello"))).message endsWith "each.")

}
