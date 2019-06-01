package one.shn.dog

import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.Executors

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits._
import fs2.{Pipe, Stream}
import one.shn.dog.domain.Log

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.language.postfixOps

object Exercise extends IOApp {

  val blockingExecutionContext: Resource[IO, ExecutionContextExecutorService] =
    (Resource make IO(ExecutionContext fromExecutorService (Executors newFixedThreadPool 1)))(ec => IO(ec shutdown ()))

  def monitoring(threshold: Int): Pipe[IO, Log, Unit] = _
    .groupAdjacentBy(_.timestamp.toEpochMilli / 10000) // group in 10 sec chunks
    .map(_._2.toVector groupBy (_.section) mapValues (_.length)) // hits by section
    .evalTap(stats => IO(print(s"\r$stats")))
    .sliding(12) // 2 min = 12 * 10 sec
    .map(_ flatMap (_.values) sum)  // all
    .map(_ > threshold * 120) // 2 min = 120 sec
    .filterWithPrevious(_ != _) // detect change in alert status
    .evalMap(busy => IO(println(if (busy) "\rToo busy." else "\rNormal.")))

  def logsSince(start: Instant)(blockingEC: ExecutionContext): Stream[IO, Log] =
    in.readLogLines(Paths get "/tmp/access.log", blockingEC)
      .map(Log.parse)
      .collect { case Some(log) => log }
      .filter(_.timestamp isAfter start)

  def job(start: Instant, threshold: Int): Stream[IO, Unit] =
    Stream resource blockingExecutionContext flatMap { blockingEC =>
      logsSince(start)(blockingEC) through monitoring(threshold)
    }

  override def run(args: List[String]): IO[ExitCode] =
    IO(Instant.now) flatMap (start => job(start, 10).compile.drain) as ExitCode.Success

}
