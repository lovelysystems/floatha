import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.Test
import java.net.URL

class HACheckerTest {

    @Test
    fun testReplaceHost() {
        val s = "http://1.2.3.4:8080/ping"
        val url = URL(s)
        "http://hoschi:8080/ping" shouldEqual url.replaceHost("hoschi").toString()
    }
}




