package info.hupel.isabelle.pmd

// FIXME copy-pasted from libisabelle
final case class CodepointIterator(string: String, offset: Int, line: Int) {
  def get: Option[(Int, CodepointIterator)] =
    if (offset < string.length) {
      val codepoint = string.codePointAt(offset)
      val incr = if (codepoint == '\n') 1 else 0
      Some((codepoint, CodepointIterator(string, offset + Character.charCount(codepoint), line + incr)))
    }
    else
      None

  def advanceUntil(target: Int): (String, CodepointIterator) =
    if (offset < target)
      get match {
        case Some((c, next)) =>
          next.advanceUntil(target) match {
            case (str, iter) => (new String(Character.toChars(c)) + str, iter)
          }
        case None => ("", this)
      }
    else
      ("", this)
}

