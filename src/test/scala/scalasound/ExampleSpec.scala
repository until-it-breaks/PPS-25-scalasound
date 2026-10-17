package scalasound

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExampleSpec extends AnyFlatSpec with Matchers:

  "A test that" should "succeed" in {
    1 + 1 shouldBe 2
  }