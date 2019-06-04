package one.shn.dog

import java.nio.file.{Path, Paths}
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import scala.language.postfixOps

package object config {

  implicit val fmt: DateTimeFormatter = DateTimeFormatter ofPattern "HH:mm:ss" withZone ZoneId.systemDefault

  val logfile:   Path = Paths get env("ACCESSMONITOR_LOG_PATH", "/tmp/access.log")
  val threshold: Int  = env("ACESSMONITOR_ALERT_THRESHOLD", "10") toInt

  private def env(variable: String, default: String) = sys.env getOrElse (variable, default)

}
