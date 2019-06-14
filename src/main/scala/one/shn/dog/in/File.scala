package one.shn.dog.in

import java.nio.file.{Path, StandardOpenOption}

import cats.effect.{ContextShift, Sync, Timer}
import fs2.io.file.{FileHandle, pulls}
import fs2.{Pull, Stream}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.{higherKinds, postfixOps}

/**
  * The methods in this object should be contributed back to fs2.file package.
  * FS2's file operations library lacks the functionality to "`tail -f`" a file.
  */
object File {

  /**
    * Reads a file until the end and emits its content
    * in byte chunks of @param `chunkSize`.
    * Then, waits for @param `delay` time, checks if anything is appended to it.
    * Repeat.
    */
  def readIndefinitely[F[_]: Sync: ContextShift: Timer](
      path:       Path,
      blockingEC: ExecutionContext,
      chunkSize:  Int,
      delay:      FiniteDuration)
  : Stream[F, Byte] =
    pulls
      .fromPath(path, blockingEC, StandardOpenOption.READ :: Nil)
      .flatMap(c => readIndefinitelyFromFileHandle(chunkSize, delay)(c.resource))
      .stream

  private def readIndefinitelyFromFileHandle[F[_]: Timer](
      chunkSize: Int,
      delay:     FiniteDuration)(
      handle:    FileHandle[F])
  : Pull[F, Byte, Unit] =
    readFromFileHandle(chunkSize, 0, delay)(handle)

  private def readFromFileHandle[F[_]](
      chunkSize: Int,
      offset:    Long,
      delay:     FiniteDuration)(
      handle:    FileHandle[F])(
      implicit
      F:         Timer[F])
  : Pull[F, Byte, Unit] =
    Pull eval handle.read(chunkSize, offset) flatMap {
      case Some(bytes) =>
        (Pull output bytes) >> readFromFileHandle(chunkSize, offset + bytes.size, delay)(handle)
      case None =>
        (Pull eval (F sleep delay)) >> readFromFileHandle(chunkSize, offset, delay)(handle)
    }

}
