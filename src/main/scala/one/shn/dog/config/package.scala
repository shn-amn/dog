package one.shn.dog

import java.time.ZoneId
import java.time.format.DateTimeFormatter

package object config {

  val fmt: DateTimeFormatter = DateTimeFormatter ofPattern "HH:mm:ss" withZone ZoneId.systemDefault

}
