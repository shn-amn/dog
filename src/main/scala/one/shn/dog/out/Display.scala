package one.shn.dog.out

import cats.effect.IO

import scala.collection.mutable.Queue
import scala.io.AnsiColor

object Display extends AnsiColor {

  sealed trait Line {
    def message: String
  }

  case class Transient(message: String) extends Line

  case class Permanent(message: String) extends Line

  private val transientBuffer: Queue[Line] = Queue.empty
  private val maxTransientLines: Int = 10

  def transient(message: String): Unit = newLine(Transient(message))

  def permanent(message: String): Unit = newLine(Permanent(message))

  def close(message: String = ""): Unit = {
    refreshAndTrimTransientBufferTo(0, animation = true)
    println(message)
  }

  private def newLine(line: Line) = {
    refreshAndTrimTransientBufferTo(maxTransientLines - 1)
    transientBuffer enqueue line
    printLine(line)
  }

  private def refreshAndTrimTransientBufferTo(n: Int, animation: Boolean = false): Unit =
    if (transientBuffer.length > n) {
      eraseTransientLines
      transientBuffer.dequeue match {
        case line: Permanent => printLine(line)
        case _ =>
      }
      transientBuffer foreach printLine
      if (animation) Thread sleep 500
      refreshAndTrimTransientBufferTo(n, animation)
    }

  private def eraseTransientLines: Unit =
    print(s"${cursorPreviousLine(transientBuffer.length)}$eraseInDisplay")

  private def printLine(line: Line): Unit = line match {
    case Transient(message) => println(s"$RESET$message$RESET")
    case Permanent(message) => println(s"$RESET$RED$message$RESET")
  }

  /**
    * CPL: `CSI n F`
    * Moves cursor to the beginning of the line, `n` line up.
    */
  def cursorPreviousLine(n: Int) = s"\u001B[${n}F"

  /**
    * ED: `CSI 0 J`
    * Clears the screen from cursor to the end.
    */
  val eraseInDisplay = "\u001B[0J"

  def isPrime(n: Int) =
    if (n < 2) false
    else 2 until n forall (n % _ != 0)

  def isSquare(n: Int) =
    1 to n exists (i => i * i == n)

  def test: Unit = {
    1 to 100 foreach { n =>
      Thread sleep 500
      if (isSquare(n)) permanent(s"$n is a perfect square.")
      else transient(s"$n is not perfect. As we all.")
    }
    close("Yay!")
  }

}
