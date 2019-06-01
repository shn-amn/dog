package one.shn.dog

import cats.effect.IO

package object out {

  def printTransient(msg: String): IO[Unit] = IO(print(s"\r$msg"))

  def printPermanent(msg: String): IO[Unit] = IO(println(s"\r$msg"))

}
