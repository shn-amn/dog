import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scala.reflect.io.File
import scala.util.Random

object LogWriter {

  val timeFormatter = DateTimeFormatter ofPattern "dd/MMM/yyyy:HH:mm:ss"
  def log(time: LocalDateTime) = s"""127.0.0.1 - james [${timeFormatter format time} +0200] "GET /report HTTP/1.0" 200 123\n"""
  val file = File("/Users/shn/tmp/access.log")

  def go(time: Int, delay: Int): Unit =
    if (time > 0) {
      file appendAll log(LocalDateTime.now)
      Thread sleep delay
      go(time - delay, delay)
    }

  def goRand(time: Int, avgDelay: Int): Unit =
    if (time > 0) {
      file appendAll log(LocalDateTime.now)
      val delay = Random nextInt avgDelay * 2
      Thread sleep delay
      goRand(time - delay, avgDelay)
    }

}
