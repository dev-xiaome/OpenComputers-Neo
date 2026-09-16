package li.cil.oc.server.component

import org.junit.runner.RunWith
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets

@RunWith(classOf[JUnitRunner])
class InternetCardHttpTest extends AnyFunSpec with Matchers {
  describe("Internet Card HTTP response handling") {
    it("uses the input stream for successful responses") {
      val connection = new StubHttpURLConnection(200, "ok", Some("ignored"))

      read(InternetCard.responseStream(connection, allowErrorBody = false)) shouldBe "ok"
      connection.inputStreamCalls shouldBe 1
      connection.errorStreamCalls shouldBe 0
    }

    it("preserves the legacy exception behavior for error responses") {
      val connection = new StubHttpURLConnection(404, "unused", Some("not found"))

      intercept[IOException] {
        InternetCard.responseStream(connection, allowErrorBody = false)
      }
      connection.inputStreamCalls shouldBe 1
      connection.errorStreamCalls shouldBe 0
    }

    it("returns an error response body when explicitly allowed") {
      val connection = new StubHttpURLConnection(404, "unused", Some("not found"))

      read(InternetCard.responseStream(connection, allowErrorBody = true)) shouldBe "not found"
      connection.inputStreamCalls shouldBe 0
      connection.errorStreamCalls shouldBe 1
    }

    it("returns an empty stream when an allowed error has no body") {
      val connection = new StubHttpURLConnection(503, "unused", None)

      read(InternetCard.responseStream(connection, allowErrorBody = true)) shouldBe ""
      connection.inputStreamCalls shouldBe 0
      connection.errorStreamCalls shouldBe 1
    }

    it("does not use the error stream for successful responses even when enabled") {
      val connection = new StubHttpURLConnection(204, "", Some("must not be read"))

      read(InternetCard.responseStream(connection, allowErrorBody = true)) shouldBe ""
      connection.inputStreamCalls shouldBe 1
      connection.errorStreamCalls shouldBe 0
    }
  }

  private def read(stream: InputStream): String = {
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    finally stream.close()
  }

  private class StubHttpURLConnection(responseCode: Int, inputBody: String, errorBody: Option[String])
    extends HttpURLConnection(new URL("http://example.invalid")) {
    var inputStreamCalls = 0
    var errorStreamCalls = 0

    override def connect(): Unit = ()

    override def disconnect(): Unit = ()

    override def usingProxy(): Boolean = false

    override def getResponseCode: Int = responseCode

    override def getInputStream: InputStream = {
      inputStreamCalls += 1
      if (responseCode >= 300) throw new IOException("HTTP error")
      new ByteArrayInputStream(inputBody.getBytes(StandardCharsets.UTF_8))
    }

    override def getErrorStream: InputStream = {
      errorStreamCalls += 1
      errorBody.map(body => new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))).orNull
    }
  }
}
