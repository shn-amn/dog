package one.shn.dog

import java.time.Instant

import fs2.Stream
import one.shn.dog.domain.{Alert, Log, Recovery, Stats}
import org.scalatest.FlatSpec

import scala.language.postfixOps

class StreamsSpec extends FlatSpec {

  private def lg(t: Int) = Log(Instant ofEpochSecond t, "/")
  private def rl(t: Int) = Right(lg(t))
  private def li(t: Int) = Left(Instant ofEpochSecond t)

  "Stats aggregation" should "properly group a mix of logs and timestamps to 10-second stats" in
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

  // The following is the REQUIRED TEST FOR THE ALERTING LOGIC

  private def st(t: Int, n: Int) = Stats(Instant ofEpochSecond t, 1 to n map (_ => lg(t - 1)) toVector)
  private def dozen(i: Int, n: Int) = i * 12 + 1 to i * 12 + 12 map (t => st(t * 10, n * 10)) toList

  private val calmStream     = Stream(dozen(0, 10) ::: dozen(0, 10): _*)
  private val alertingStream = Stream(dozen(0, 10) ::: dozen(1, 15) ::: dozen(2, 5): _*)

  "Alerting logic" should "not raise an alert when there is none" in
    assert(calmStream.through(Streams scanForAlerts 10).toList.isEmpty)

  private val signals = alertingStream through (Streams scanForAlerts 10) toList

  it should "raise an alert as soon as the 2-min average is above threshold" in
    assert(signals.head == Alert(Instant ofEpochSecond 130, 1250, 10)) // avg = 1250/120 > 10

  it should "not raise any more alerts until the first alert is recovered" in
    assert(signals.tail.headOption forall (_.isInstanceOf[Recovery]))

  it should "signal a recovery as soon as the 2-min average is again below or equal to the threshold" in
    assert(signals find (_.isInstanceOf[Recovery]) contains Recovery(Instant ofEpochSecond 300, 1200, 10)) // avg = 1200/120 = 10

}
