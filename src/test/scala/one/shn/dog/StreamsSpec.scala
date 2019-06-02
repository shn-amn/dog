package one.shn.dog

import java.time.Instant

import fs2.Stream
import one.shn.dog.domain.{Busy, Log, Normal, Stats}
import org.scalatest.FlatSpec

import scala.language.postfixOps

class StreamsSpec extends FlatSpec {

  def lg(t: Int) = Log(Instant ofEpochSecond t, "/")
  def rl(t: Int) = Right(lg(t))
  def li(t: Int) = Left(Instant ofEpochSecond t)

  "Streams.groupToStats" should "properly group a mix of logs and timestamps to 10-second stats" in
    assert(Stream(
      rl(5), li(5), li(6), rl(7), li(7), li(8), li(9),
      li(10), rl(10), rl(10), rl(11), rl(12), rl(12), rl(12), li(13), li(14), li(15), li(16), li(17), li(18), li(19),
      li(20), rl(21), rl(22), rl(22), rl(22), rl(23), rl(23), li(23),
      li(35))
      .through(Streams.groupToStats)
      .toList == List(
        Stats(
          timestamp = Instant ofEpochSecond 20,
          logs      = Vector(lg(10), lg(10), lg(11), lg(12), lg(12), lg(12))),
        Stats(
          timestamp = Instant ofEpochSecond 30,
          logs      = Vector(lg(21), lg(22), lg(22), lg(22), lg(23), lg(23))),
        Stats(
          timestamp = Instant ofEpochSecond 40,
          logs      = Vector())))

  def st(t: Int, n: Int) = Stats(
    timestamp = Instant ofEpochSecond t,
    logs      = 1 to n map (_ => lg(t - 1)) toVector)
  def dozen(i: Int, n: Int) = i * 12 + 1 to i * 12 + 12 map (t => st(t * 10, n * 10)) toList

  "Streams.scanForAlerts" should "raise and drop alerts at the right time" in {
    assert(Stream(dozen(0, 5) ::: dozen(1, 15) ::: dozen(2, 20) ::: dozen(3, 10): _*)
      .through(Streams.scanForAlerts(10))             // alert level is 1200 hits per 2 minutes
      .toList == List(
        Normal(Instant ofEpochSecond 120, 600, 10),   // 12 * 5 = 60 < 120 ok
        Busy(Instant ofEpochSecond 190, 1300, 10),    // 6 * 5 + 6 * 15 = 120 ok; 5 * 5 + 7 * 15 = 130 > 120 alert
        Normal(Instant ofEpochSecond 480, 1200, 10))) // 1 * 20 + 11 * 10 = 130 > 120 still not ok; 12 * 10 = 120 ok
  }


}
