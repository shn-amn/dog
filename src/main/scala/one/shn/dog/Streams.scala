package one.shn.dog

import java.nio.file.{Path, Paths}
import java.time.Instant

import cats.effect.{Concurrent, ContextShift, IO, Timer}
import cats.implicits._
import fs2.{Pipe, Stream}
import one.shn.dog.domain.{Alert, Busy, Log, Normal, Stats}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object Streams {

  def logsSince(
      path:       Path,
      since:      Instant,
      blockingEC: ExecutionContext)(
      implicit
      cs:         ContextShift[IO],
      timer:      Timer[IO])
  : Stream[IO, Log] =
    in.readLogLines(path, blockingEC)
      .map(Log.parse)
      .collect { case Some(log) => log }
      .filter(_.timestamp isAfter since)

  def heartbeat(
      lag:   Duration)(
      implicit
      timer: Timer[IO])
  : Stream[IO, Instant] =
    Stream
      .awakeEvery[IO](1 second)
      .evalMap(_ => timer.clock.realTime(MILLISECONDS) map (_ - lag.toMillis))
      .map(Instant.ofEpochMilli)

  def groupToStats(
      implicit
      timer: Timer[IO],
      con:   Concurrent[IO])
  : Pipe[IO, Log, Stats] = _
    .map(Right.apply)
    .merge(heartbeat(lag = 500 millis) map Left.apply)
    .filterWithPrevious(notDisorderedHeartbeat)
    .groupAdjacentBy(timestamp(_).toEpochMilli / 10000)
    .drop(1) // The first one does not necessarily represent 10 seconds
    .map { case (tenthSeconds, logsAndHeartbeats) => Stats(
      timestamp = Instant ofEpochSecond 10 * (tenthSeconds + 1),
      logs      = logsAndHeartbeats collect { case Right(log) => log } toVector)
    }

  private def notDisorderedHeartbeat(
      prev: Either[Instant, Log],
      curr: Either[Instant, Log]) =
    curr.isRight || !(timestamp(prev) isAfter timestamp(curr))

  private def timestamp(x: Either[Instant, Log]) = x match {
    case Left(instant) => instant
    case Right(log)    => log.timestamp
  }

  def scanForAlerts(threshold: Int)
  : Pipe[IO, Stats, Alert] = _
    .sliding(12) // 2 min = 12 * 10 sec
    .map(stats => (stats map (_.count) sum, stats.last.timestamp))
    .map { case (count, timestamp) => (count > threshold * 120, count, timestamp) } // 2 min = 120 sec
    .filterWithPrevious(_._1 != _._1) // detect change in alert status
    .map {
      case (true, count, timestamp)  => Busy(timestamp, count, threshold)
      case (false, count, timestamp) => Normal(timestamp, count, threshold)
    }

}
