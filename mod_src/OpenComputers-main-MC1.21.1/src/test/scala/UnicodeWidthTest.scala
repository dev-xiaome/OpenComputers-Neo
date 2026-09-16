import li.cil.oc.util.{FontUtils, PackedColor, TextBuffer}
import org.junit.runner.RunWith
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UnicodeWidthTest extends AnyFunSpec with Matchers {
  describe("Unicode display widths") {
    it("classifies zero-width space as zero columns") {
      FontUtils.wcwidth(0x200B) should be(0)
    }

    it("truncates by display width without splitting supplementary code points") {
      FontUtils.wtrunc("A\u200BB", 2) should be("A\u200B")
      FontUtils.wtrunc("\uD83D\uDE00B", 3) should be("\uD83D\uDE00")
    }

    it("does not allocate a text-buffer cell to zero-width space") {
      val buffer = new TextBuffer(10, 1, new PackedColor.SingleBitFormat(0xFFFFFF))

      buffer.set(0, 0, "Mic\u200Bhiyo", vertical = false) should be(true)

      buffer.lineToString(0).take(7) should be("Michiyo")
      buffer.get(7, 0) should be(' ')
    }
  }
}
