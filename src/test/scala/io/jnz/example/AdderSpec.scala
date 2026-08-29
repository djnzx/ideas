package io.jnz.example

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class AdderSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  import Adder._

  /** Biased towards the boundary, where all the interesting behaviour lives. */
  private val ints: Gen[Int] = Gen.oneOf(
    Gen.chooseNum(Int.MinValue, Int.MaxValue),
    Gen.oneOf(Int.MinValue, Int.MinValue + 1, -1, 0, 1, Int.MaxValue - 1, Int.MaxValue),
    Gen.chooseNum(Int.MaxValue - 100, Int.MaxValue),
    Gen.chooseNum(Int.MinValue, Int.MinValue + 100)
  )

  private def pairs = forAll(ints, ints) _

  "overflows agrees with the Long oracle" in pairs { (a, b) =>
    val exact = a.toLong + b.toLong
    overflows(a, b) shouldBe (exact < Int.MinValue.toLong || exact > Int.MaxValue.toLong)
  }

  "when the sum fits, every variant returns it" in pairs { (a, b) =>
    whenever(!overflows(a, b)) {
      val expected = a.toLong + b.toLong
      add(a, b).toLong shouldBe expected
      addSaturating(a, b).toLong shouldBe expected
      addOption(a, b) shouldBe Some(expected.toInt)
      addEither(a, b) shouldBe Right(expected.toInt)
      addExact(a, b).toLong shouldBe expected
      addWidened(a, b) shouldBe expected
      addWithCarry(a, b) shouldBe ((expected.toInt, false))
      addFold(a, b)((_, _) => -1L, _.toLong) shouldBe expected
    }
  }

  "on overflow" - {
    "saturating clamps in the direction of the operands" in pairs { (a, b) =>
      whenever(overflows(a, b)) {
        addSaturating(a, b) shouldBe (if (a > 0) Int.MaxValue else Int.MinValue)
      }
    }

    "Option is empty, Either keeps the exact Long, addExact throws" in pairs { (a, b) =>
      whenever(overflows(a, b)) {
        val exact = a.toLong + b.toLong
        addOption(a, b) shouldBe None
        addEither(a, b) shouldBe Left(exact)
        addWithCarry(a, b)._2 shouldBe true
        assertThrows[ArithmeticException](addExact(a, b))
      }
    }

    "widening variants never fail" in pairs { (a, b) =>
      addWidened(a, b) shouldBe a.toLong + b.toLong
    }
  }

  "folding a checked add depends on the order of the summands" in {
    // the total fits, but one ordering hits an overflowing prefix and the other does not
    val xs = List(Int.MaxValue, 1, -1)
    def foldChecked(l: List[Int]): Option[Int] =
      l.foldLeft(Option(0))((acc, x) => acc.flatMap(addOption(_, x)))
    foldChecked(xs) shouldBe None
    foldChecked(xs.reverse) shouldBe Some(Int.MaxValue)
  }

  "saturating addition is not associative, so it forms no Monoid" in {
    addSaturating(addSaturating(Int.MaxValue, 1), -1) shouldBe Int.MaxValue - 1
    addSaturating(Int.MaxValue, addSaturating(1, -1)) shouldBe Int.MaxValue
  }

  "checked addition is not associative either" in {
    addOption(Int.MaxValue, 1).flatMap(addOption(_, -1)) shouldBe None
    addOption(1, -1).flatMap(addOption(Int.MaxValue, _)) shouldBe Some(Int.MaxValue)
  }

  "wrapping addition is associative -- the only lawful choice, and already cats' Monoid[Int]" in
    forAll(ints, ints, ints) { (a, b, c) =>
      add(add(a, b), c) shouldBe add(a, add(b, c))
    }
}
