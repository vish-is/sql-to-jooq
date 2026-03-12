plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
    kotlin("jvm") version "1.9.25"
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "io.github.vishnyak"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website = "https://github.com/vish-is/sql-to-jooq"
    vcsUrl = "https://github.com/vish-is/sql-to-jooq"

    plugins {
        create("sqlToJooq") {
            id = "io.github.vish-is.sql-to-jooq"
            displayName = "SQL to jOOQ Code Generator"
            description = "Generates jOOQ Record, Pojo, Tables and Repository classes from SQL migrations (Liquibase/Flyway)"
            tags = listOf("jooq", "sql", "codegen", "liquibase", "flyway", "kotlin")
            implementationClass = "sqltojooq.SqlToJooqPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
    isRequired = false
}
