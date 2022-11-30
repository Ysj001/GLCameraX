import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.io.File
import java.net.URI

/*
 * Maven 发布用的常量
 *
 * @author Ysj
 * Create time: 2022/11/8
 */

val Project.MAVEN_LOCAL: URI
    get() = File(rootDir, "repos").toURI()

const val LIB_VERSION = "1.0.0"
const val LIB_GROUP_ID = "com.ysj.lib"

const val POM_URL = "https://github.com/Ysj001/GLCameraX.git"

const val POM_DEVELOPER_ID = "Ysj"
const val POM_DEVELOPER_NAME = "Ysj"

fun Project.applyMavenLocal(handler: RepositoryHandler) = handler.maven {
    url = MAVEN_LOCAL
}
