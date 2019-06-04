package one.shn.dog

import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.Executors

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits._
import fs2.Stream
import one.shn.dog.Streams._
import one.shn.dog.out.Display

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.concurrent.duration._
import scala.language.postfixOps

object AccessLogMonitor extends IOApp {

  import config._

  val blockingExecutionContext: Resource[IO, ExecutionContextExecutorService] =
    (Resource make IO(ExecutionContext fromExecutorService (Executors newFixedThreadPool 1)))(ec => IO(ec shutdown ()))

  def job(start: Instant): Stream[IO, Unit] =
    Stream resource blockingExecutionContext flatMap { blockingEC =>
      parseLogs(path = logfile, since = start, blockingEC)
        .through(addHeartbeat(lag = heartbeatLag millis))
        .through(groupToStats)
        .evalTap(stats => IO(Display transient stats.message))
        .through(scanForAlerts(threshold))
        .evalMap(signal => IO(Display permanent (signal.message, signal.color)))
    }

  override def run(args: List[String]): IO[ExitCode] =
    IO(Instant.now) flatMap (start => job(start).compile.drain) as ExitCode.Success

}
