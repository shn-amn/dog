package one.shn.dog.domain

import java.time.Instant
import java.time.format.DateTimeFormatter

import scala.language.postfixOps
import scala.util.Try
import scala.util.matching.Regex

case class Log(timestamp: Instant, section: String)

object Log {

  val logLine: Regex = raw"""^[\d.:]+\s\S*-\S*\s\S*\s\[([^\]]+)\]\s"\S*\s(/\S*)\S*\s\S*"\s\d{3}\s\d+""".r

  /**
    * All accepted date and time formats.
    */
  val timeFormats: List[String] = "dd/MMM/yyyy:HH:mm:ss xx" :: "dd/MMMM/yyyy:HH:mm:ss xx" :: Nil

  private val formatters: List[DateTimeFormatter] = timeFormats map DateTimeFormatter.ofPattern

  // Tries to parse date and time with all the formats above, picks the first one working.
  private def parseTime(s: String): Option[Instant] =
    (formatters map (fmt => Try(fmt parse (s, Instant from _)) toOption) foldLeft (None: Option[Instant])) (_ orElse _)

  /**
    * If input string matches `logLine` regex and date/time format is supported, returns parsed log.
    * Otherwise returns `None`.
    */
  def parse(line: String): Option[Log] = line match {
    case logLine(timestamp, section) => parseTime(timestamp) map (Log(_, section))
    case _                           => None
  }

}
