package io.jnz.example

object Adder {

  // ---------------------------------------------------------------------------------------------
  // The predicate everything else is built on
  // ---------------------------------------------------------------------------------------------

  /** True iff `a + b` does not fit in an `Int`. Branch-free: overflow requires equal operand signs
    * and shows up as the result's sign flipping, which `(a ^ r) & (b ^ r) < 0` detects.
    */
  def overflows(a: Int, b: Int): Boolean = {
    val r = a + b
    ((a ^ r) & (b ^ r)) < 0
  }

  // ---------------------------------------------------------------------------------------------
  // 1. Unsafe -- wrap around silently (the JVM's `+`)
  // ---------------------------------------------------------------------------------------------

  /** Two's-complement addition mod 2^32. Never fails, sometimes lies. */
  def add(a: Int, b: Int): Int = a + b

  // ---------------------------------------------------------------------------------------------
  // 2. Saturating -- clamp at the boundary
  // ---------------------------------------------------------------------------------------------

  /** Clamps to `Int.MaxValue` on positive overflow and `Int.MinValue` on negative -- the second case
    * is the one the usual "keep MaxValue" phrasing forgets.
    */
  def addSaturating(a: Int, b: Int): Int =
    if (!overflows(a, b)) a + b
    else if (a > 0) Int.MaxValue
    else Int.MinValue

  // ---------------------------------------------------------------------------------------------
  // 3. Option -- "it didn't work" with no detail
  // ---------------------------------------------------------------------------------------------

  /** `None` on overflow: the minimal signal, carrying neither a value nor a reason. */
  def addOption(a: Int, b: Int): Option[Int] =
    if (overflows(a, b)) None else Some(a + b)

  // ---------------------------------------------------------------------------------------------
  // 4. Either -- the exact result survives as a Long
  // ---------------------------------------------------------------------------------------------

  /** `Left` carries the true 64-bit sum, so this is the only in-band variant that loses nothing:
    * the caller recovers the real answer instead of just learning one existed.
    */
  def addEither(a: Int, b: Int): Either[Long, Int] = {
    val exact = a.toLong + b.toLong
    if (exact.toInt.toLong == exact) Right(exact.toInt) else Left(exact)
  }

  // ---------------------------------------------------------------------------------------------
  // 5. Throwing
  // ---------------------------------------------------------------------------------------------

  /** Throws instead of wrapping. Delegates to `Math.addExact`, which HotSpot intrinsifies to a
    * single `ADD` plus a `JO`; the exception carries nothing the caller does not already have.
    */
  @throws[ArithmeticException]("when a + b does not fit in an Int")
  def addExact(a: Int, b: Int): Int = Math.addExact(a, b)

  // ---------------------------------------------------------------------------------------------
  // Widen instead of failing
  // ---------------------------------------------------------------------------------------------

  /** Total by construction: no two `Int`s can overflow a `Long`. */
  def addWidened(a: Int, b: Int): Long = a.toLong + b.toLong

  /** The wrapped result paired with its carry-out flag, like `ADC` on x86. */
  def addWithCarry(a: Int, b: Int): (Int, Boolean) = (a + b, overflows(a, b))

  // ---------------------------------------------------------------------------------------------
  // Caller decides what overflow means
  // ---------------------------------------------------------------------------------------------

  /** The universal eliminator: every variant above is this fold under a different pair of
    * continuations.
    */
  def addFold[A](a: Int, b: Int)(onOverflow: (Int, Int) => A, onSuccess: Int => A): A =
    if (overflows(a, b)) onOverflow(a, b) else onSuccess(a + b)

}
