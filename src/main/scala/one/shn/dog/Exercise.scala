package one.shn.dog

import java.time.Instant

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2.{Pipe, Stream}

import scala.language.postfixOps

object Exercise extends IOApp {

  case class Log(timestamp: Instant, section: String)

  def monitoring(threshold: Int): Pipe[IO, Log, Unit] = _
    .groupAdjacentBy(_.timestamp.toEpochMilli / 10000) // group in 10 sec chunks
    .map(_._2.toVector groupBy (_.section) mapValues (_.length)) // hits by section
    .evalTap(stats => IO(print(s"\r$stats")))
    .sliding(12) // 2 min = 12 * 10 sec
    .map(_ flatMap (_.values) sum)  // all
    .map(_ > threshold * 120) // 2 min = 120 sec
    .filterWithPrevious(_ != _) // detect change in alert status
    .evalMap(busy => IO(println(if (busy) "\rToo busy." else "\rNormal.")))

  val logStream: Stream[IO, Log] = ???

  override def run(args: List[String]): IO[ExitCode] =
    logStream.through(monitoring(10)).compile.drain as ExitCode.Success

}
