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

object Exercise extends IOApp {

  val blockingExecutionContext: Resource[IO, ExecutionContextExecutorService] =
    (Resource make IO(ExecutionContext fromExecutorService (Executors newFixedThreadPool 1)))(ec => IO(ec shutdown ()))

  def job(since: Instant, threshold: Int): Stream[IO, Unit] =
    Stream resource blockingExecutionContext flatMap { blockingEC =>
      logsSince(Paths get "/tmp/access.log", since, blockingEC)
        .through(addHeartbeat(lag = 500 millis))
        .through(groupToStats)
        .evalTap(stats => IO(Display transient stats.message))
        .through(scanForAlerts(10))
        .evalMap(alert => IO(Display permanent alert.message))
    }

  override def run(args: List[String]): IO[ExitCode] =
    IO(Instant.now) flatMap (start => job(start, 10).compile.drain) as ExitCode.Success

}
