package one.shn.dog.domain

import org.scalatest.FlatSpec

import scala.language.postfixOps

class LogSpec extends FlatSpec {

  val providedExamples =
    """127.0.0.1 - james [09/May/2018:16:00:39 +0000] "GET /report HTTP/1.0" 200 123""" ::
    """127.0.0.1 - jill [09/May/2018:16:00:41 +0000] "GET /api/user HTTP/1.0" 200 234""" ::
    """127.0.0.1 - frank [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 200 34""" ::
    """127.0.0.1 - mary [09/May/2018:16:00:42 +0000] "POST /api/user HTTP/1.0" 503 12""" :: Nil

  val wikipediaExample =
    """127.0.0.1 user-identifier frank [10/Oct/2000:13:55:36 -0700] "GET /apache_pb.gif HTTP/1.0" 200 2326"""

  val invalidExamples =
    """127.0.0.1 [09/May/2018:16:00:39 +0000] "GET /report HTTP/1.0" 200 123""" ::
    """127.0.0.1 - jill [09/BAD/2018:16:00:41 +0000] "GET /api/user HTTP/1.0" 200 234""" :: Nil


  "Log" should "successfully parse all provided examples" in
    assert((providedExamples flatMap Log.parse length) == providedExamples.length)

  it should "successfully parse the example in Wikipedia" in
    assert(Log parse wikipediaExample nonEmpty)

  it should "fail to parse invalid lines" in
    assert(invalidExamples flatMap Log.parse isEmpty)

}
