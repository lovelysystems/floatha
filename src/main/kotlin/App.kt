import com.myjeeva.digitalocean.exception.DigitalOceanException
import com.myjeeva.digitalocean.impl.DigitalOceanClient
import com.myjeeva.digitalocean.pojo.Droplet
import com.myjeeva.digitalocean.pojo.FloatingIP
import com.natpryce.konfig.*
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.timer

val FLOATING_HEALTH_URI by stringType
val DROPLET_TAG by stringType
val RETRY_WAIT_TIME by longType
val CHECK_WAIT_TIME by longType
val NUM_RETRIES by intType

fun main(args: Array<String>) {
    val config = EnvironmentVariables() overriding ConfigurationMap(
        CHECK_WAIT_TIME to "1000",
        RETRY_WAIT_TIME to "1000",
        NUM_RETRIES to "5"
    )

    val checker = HAChecker(
        config[FLOATING_HEALTH_URI],
        config[DROPLET_TAG],
        config[RETRY_WAIT_TIME],
        config[NUM_RETRIES],
        readDOToken()
    )
    timer("Checker", period = config[CHECK_WAIT_TIME]) { checker.trigger() }
}

fun readDOToken(): String {
    val configFile = File(System.getProperty("user.home")).resolve(".config/doctl/config.yaml")
    for (line in configFile.readLines()) {
        if (line.trim().startsWith("access-token:")) {
            return line.trim().split("access-token:").last().trim()
        }
    }
    throw java.lang.RuntimeException("Unable to get access token from $configFile")
}

data class HAChecker(
    val floatingHealthURIString: String,
    val dropletTag: String,
    val retryWaitTime: Long,
    val numRetries: Int,
    val doToken: String,
    val usePrivate: Boolean = true
) {

    private val logger = Logger.getLogger("HAChecker")
    private var floatingHealthURL: URL = URL(floatingHealthURIString)
    private val doClient: DigitalOceanClient = DigitalOceanClient(doToken)
    private var currentDroplet: Droplet
    private val floatingIP: String = floatingHealthURL.host
    private var currentHealthURL: URL

    init {
        logger.level = Level.INFO
        currentDroplet = ensureAssignedDroplet()
        currentHealthURL = healthURL()
    }

    private fun healthURL(): URL {
        return if (usePrivate) {
            floatingHealthURL.replaceHost(currentDroplet.privateIP)
        } else {
            floatingHealthURL
        }
    }

    private fun ensureAssignedDroplet(): Droplet {
        val current = getCurrentDroplet()
        if (current != null) return current
        return reassign()
    }

    private fun assign(droplet: Droplet) {
        logger.warning("assigning $floatingIP to droplet ${droplet.displayName}")
        currentDroplet = droplet
        doClient.assignFloatingIP(droplet.id, floatingIP)
        currentHealthURL = healthURL()
    }

    private fun reassign(): Droplet {
        for (droplet in getAvailableDroplets()) {
            if (currentDroplet.id == droplet.id) {
                logger.info("ignoring already assigned droplet")
                continue
            }
            val dropletHeathURL = floatingHealthURL.replaceHost(droplet.privateIP)
            if (checkHealth(dropletHeathURL, 0)) {
                assign(droplet)
                return droplet
            } else {
                logger.warning("unhealthy droplet found ${droplet.displayName} $dropletHeathURL")
            }
        }
        throw RuntimeException("No more droplets available")
    }

    fun trigger() {
        if (checkHealth()) return
        logger.warning("health check failed on ${currentDroplet.displayName}")
        reassign()
        logger.info("assigned droplet ${currentDroplet.displayName}")
    }

    private fun getAvailableDroplets(): List<Droplet> {
        return doClient.getAvailableDropletsByTagName(dropletTag, 0, 10)
            .droplets.filter { it.isActive }
    }

    private fun getCurrentDroplet(): Droplet? {
        val floatingIPInfo: FloatingIP
        try {
            floatingIPInfo = doClient.getFloatingIPInfo(floatingIP)
        } catch (e: DigitalOceanException) {
            throw java.lang.RuntimeException("No floating IP found for address $floatingIP")
        }
        val droplet = floatingIPInfo.droplet
        if (droplet == null) {
            logger.warning { "not droplet assigned to $floatingIP" }
        } else {
            logger.info { "droplet found for $floatingIP: ${droplet.displayName}" }
        }
        return droplet
    }

    private fun checkHealth(url: URL = currentHealthURL, retries: Int = numRetries): Boolean {
        var tries = 0
        while (tries < retries + 1) {
            if (tries > 0) {
                Thread.sleep(retryWaitTime)
            }
            tries++
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            val status = try {
                conn.responseCode
            } catch (e: IOException) {
                -2
            }
            if (status == 200) {
                return true
            }
            logger.warning("healthcheck failed: tries=$tries url=$url")
        }
        return false
    }
}

private val Droplet.privateIP: String
    get() {
        return networks.version4Networks.first { it.type == "private" }.ipAddress
    }

private val Droplet.displayName: String
    get() {
        return "name=$name id=$id"
    }

fun URL.replaceHost(newHost: String): URL {
    return URL(protocol, newHost, port, file)
}
