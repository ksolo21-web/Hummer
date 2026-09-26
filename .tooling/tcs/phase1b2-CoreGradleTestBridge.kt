package com.koenterprises.territorycardstudio.core

import java.io.File
import java.lang.reflect.InvocationTargetException
import org.junit.jupiter.api.Test

class CoreGradleTestBridge {
    @Test
    fun fullCoreContractSmoke() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile &&
                    File(candidate, "app/src/main/assets/territory").isDirectory
            }
            ?: error("Unable to resolve Territory Card Studio project root from ${System.getProperty("user.dir")}")

        val assets = File(root, "app/src/main/assets/territory")
        val exactApprovedFixture = File(root, "core/src/test/resources/pdf-boundary/Territory - 300Aa.pdf")
        check(assets.isDirectory) { "Missing territory asset directory: $assets" }
        check(exactApprovedFixture.isFile) { "Missing exact-approved PDF fixture: $exactApprovedFixture" }

        val mainClass = Class.forName("com.koenterprises.territorycardstudio.core.CoreContractSmokeTestKt")
        val mainMethod = mainClass.getMethod("main", arrayOf<String>()::class.java)
        try {
            mainMethod.invoke(null, arrayOf(assets.absolutePath, exactApprovedFixture.absolutePath) as Any)
        } catch (failure: InvocationTargetException) {
            throw (failure.targetException ?: failure)
        }
    }
}
