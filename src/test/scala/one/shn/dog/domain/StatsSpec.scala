package one.shn.dog.domain

import java.time.Instant

import org.scalatest.FlatSpec

class StatsSpec extends FlatSpec {

  private val timestamp = Instant ofEpochMilli 0

  s"Stats($timestamp, Vector.empty)" should s"""read "$timestamp: No hits."""" in
    assert(Stats(timestamp, Vector.empty).message == s"$timestamp: No hits.")

  s"Stats($timestamp, Vector(log))" should "have one popular section." in
    assert(Stats(timestamp, Vector(Log(timestamp, "/"))).message endsWith "100%.")

  s"Stats($timestamp, Vector(log1, log2)" should "have two popular sections." in
    assert(Stats(timestamp, Vector(Log(timestamp, "/"), Log(timestamp, "/hello"))).message endsWith "each.")

}
