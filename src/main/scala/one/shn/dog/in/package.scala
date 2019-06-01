package one.shn.dog

import java.nio.file.Path

import cats.effect.{ContextShift, IO, Sync, Timer}
import fs2.{Stream, text}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

package object in {

  def readLogLines(
      path:       Path,
      blockingEC: ExecutionContext)(
      implicit
      sync:       Sync[IO],
      cs:         ContextShift[IO],
      timer:      Timer[IO])
  : Stream[IO, String] =
    File.readIndefinitely(path, blockingEC, 1024, 250 millis)
      .through(text.utf8Decode)
      .through(text.lines)

}
