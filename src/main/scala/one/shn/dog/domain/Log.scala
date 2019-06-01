package one.shn.dog.domain

import java.time.Instant
import java.time.format.DateTimeFormatter

import scala.util.matching.Regex

case class Log(timestamp: Instant, section: String)

object Log {

  // TODO: Is regex too permissive?
  val log: Regex = raw"""^[0-9.]+ - .* \[([^\]]+)\] "[A-Z]{3,10} (/[a-z]*)[/a-z]* [A-Z/0-9.]+" [0-9]{3} [0-9]+""".r

  // TODO: Time zone offset does not seem to work!
  val format: DateTimeFormatter = DateTimeFormatter ofPattern "dd/MMMM/yyyy:HH:mm:ss xxxx"

  def parse(line: String): Option[Log] = line match {
    case log(timestamp, section) => Some(Log(format parse (timestamp, Instant from _), section))
    case _                       => None
  }

}
