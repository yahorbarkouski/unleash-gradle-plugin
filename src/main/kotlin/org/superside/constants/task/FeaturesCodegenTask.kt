package org.superside.constants.task

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction
import org.superside.constants.extension.UnleashExtension
import org.superside.constants.model.Feature
import org.superside.constants.service.FeatureFetcher
import org.superside.constants.util.toEnumStyle
import org.superside.constants.util.toFormattedLine
import org.superside.constants.util.toMapOfWords
import java.io.File
import java.nio.file.Path
import javax.inject.Inject

@CacheableTask
abstract class FeaturesCodegenTask @Inject constructor() : DefaultTask() {

    private val featureFetcher = FeatureFetcher()

    @TaskAction
    fun run() {
        val unleashExtension = project.extensions.getByType(UnleashExtension::class.java)
        assert(unleashExtension.url.isNotBlank()) { "Unleash URL must be set" }

        val generatedSrcDir = File(project.projectDir, "src/main/kotlin/")
        generatedSrcDir.mkdirs()
        try {
            generate(unleashExtension, generatedSrcDir)
        } catch (e: Exception) {
            System.err.println("Unable to generate features from unleash:")
            e.printStackTrace()
        }
    }

    private fun generate(unleashExtension: UnleashExtension, generatedSrcDir: File) {
        println(
            "Generating features to $generatedSrcDir/" +
                "${unleashExtension.packageName}/${unleashExtension.fileName}.kt"
        )

        val featureBuilder = TypeSpec.objectBuilder(unleashExtension.fileName)
            .addKdoc(
                "This class is generated by the Unleash Codegen Gradle plugin. Do not edit."
            )
            .addAnnotation(
                AnnotationSpec.Companion.builder(Suppress::class)
                    .addMember(CodeBlock.of("names = [\"unused\", \"RedundantVisibilityModifier\"]"))
                    .build()
            )

        for (feature in featureFetcher.fetchFeatures(unleashExtension.url, unleashExtension.token)) {
            featureBuilder
                .addProperty(
                    PropertySpec.builder(feature.name.toEnumStyle(), String::class, KModifier.CONST)
                        .initializer("%S", feature.name)
                        .addKdoc(generateValidDescription(feature))
                        .build()
                )
        }

        FileSpec.builder(unleashExtension.packageName, unleashExtension.fileName)
            .addType(featureBuilder.build())
            .indent("\t")
            .build()
            .writeTo(Path.of("${generatedSrcDir.path}/"))
    }

    private fun generateValidDescription(feature: Feature): String {
        var description = "Description: "
        if (feature.description?.trim()?.isNotBlank() == true) {
            description += feature.description.toMapOfWords().toFormattedLine()
        } else {
            description += "empty"
        }

        return description
    }
}
