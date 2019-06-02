package one.shn.dog.out

import scala.collection.mutable.Queue
import scala.io.AnsiColor
import scala.language.postfixOps

object Display extends AnsiColor {

  sealed trait Line {
    def message: String
    def color:   String
  }
  case class Transient(message: String, color: String) extends Line
  case class Permanent(message: String, color: String)   extends Line

  private val transientBuffer:   Queue[Line] = Queue.empty
  private val maxTransientLines: Int         = 10

  def transient(
      message: String,
      color:   String = AnsiColor.WHITE)
  : Unit =
    newLine(Transient(message, color))

  def permanent(
      message: String,
      color:   String = AnsiColor.RED)
  : Unit =
    newLine(Permanent(message, color))

  def close(
      message: String = "")
  : Unit = {
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
    case Transient(message, color) => println(s"$RESET$color$message$RESET")
    case Permanent(message, color) => println(s"$RESET$color$message$RESET")
  }

  /**
    * CPL: `CSI n F`
    * Moves cursor to the beginning of the line, `n` line up.
    */
  private def cursorPreviousLine(n: Int) = s"\u001B[${n}F"

  /**
    * ED: `CSI 0 J`
    * Clears the screen from cursor to the end.
    */
  private val eraseInDisplay = "\u001B[0J"

}
